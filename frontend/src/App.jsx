import { useCallback, useEffect, useState } from 'react';
import { api, pollPaymentUntilSettled } from './api.js';
import AccountsPanel from './components/AccountsPanel.jsx';
import SendPaymentForm from './components/SendPaymentForm.jsx';
import ActivityFeed from './components/ActivityFeed.jsx';
import LedgerDrawer from './components/LedgerDrawer.jsx';

const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
const POLL_INTERVAL_MS = 4000;

export default function App() {
  const [accounts, setAccounts] = useState([]);
  const [payments, setPayments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [backendUp, setBackendUp] = useState(null); // null = unknown yet
  const [ledgerAccount, setLedgerAccount] = useState(null);

  const refreshAccounts = useCallback(async () => {
    const list = await api.listAccounts();
    setAccounts(list);
    return list;
  }, []);

  const refreshPayments = useCallback(async () => {
    const page = await api.listPayments({ page: 0, size: 15 });
    setPayments(page.content);
    return page.content;
  }, []);

  // Connectivity is inferred from whether the actual data requests succeed,
  // rather than a separate /actuator/health ping — that endpoint isn't
  // covered by the backend's CORS config (only /api/** is), so a direct
  // fetch to it is blocked by the browser even when the API itself is
  // perfectly reachable, which was producing a false "unreachable" reading.
  const refreshAll = useCallback(async () => {
    try {
      await Promise.all([refreshAccounts(), refreshPayments()]);
      setBackendUp(true);
      return true;
    } catch (err) {
      setBackendUp(false);
      throw err;
    }
  }, [refreshAccounts, refreshPayments]);

  useEffect(() => {
    let cancelled = false;

    async function initialLoad() {
      setLoading(true);
      setError(null);
      try {
        await refreshAll();
      } catch (err) {
        if (!cancelled) setError(err.message);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    initialLoad();

    const interval = setInterval(() => {
      refreshAll().catch(() => {});
    }, POLL_INTERVAL_MS);

    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [refreshAll]);

  async function handleCreateAccount(body) {
    await api.createAccount(body);
    await refreshAccounts();
  }

  function handleSubmitPayment(body) {
    const idempotencyKey = crypto.randomUUID();
    const submitted = api.submitPayment(body, idempotencyKey);

    const settle = submitted
      .then((payment) => pollPaymentUntilSettled(payment.id))
      .then(async (settledPayment) => {
        await Promise.all([refreshAccounts(), refreshPayments()]);
        return settledPayment;
      });

    return { submitted, settle };
  }

  const accountsById = Object.fromEntries(accounts.map((a) => [a.id, a]));

  return (
    <>
      <header className="app-header">
        <div>
          <h1>Payment Ledger Platform</h1>
          <p>Distributed payment processing &amp; double-entry ledger — live against your local backend.</p>
        </div>
        <span className="status-badge">
          <span className={`status-dot ${backendUp === null ? '' : backendUp ? 'up' : 'down'}`} />
          {backendUp === null ? 'Checking backend…' : backendUp ? 'Backend connected' : 'Backend unreachable'}
        </span>
      </header>

      {error && <div className="error-banner">{error}</div>}
      {backendUp === false && (
        <div className="error-banner">
          Can't reach the API at {BASE_URL}. Make sure <code>docker compose up -d</code> and{' '}
          <code>mvn spring-boot:run</code> are running.
        </div>
      )}

      <div className="grid">
        <div>
          <AccountsPanel
            accounts={accounts}
            loading={loading}
            onSelectAccount={setLedgerAccount}
            onCreateAccount={handleCreateAccount}
          />
        </div>
        <div>
          <SendPaymentForm accounts={accounts} onSubmitPayment={handleSubmitPayment} />
        </div>
      </div>

      <div style={{ marginTop: 20 }}>
        <ActivityFeed payments={payments} accountsById={accountsById} loading={loading} />
      </div>

      {ledgerAccount && <LedgerDrawer account={ledgerAccount} onClose={() => setLedgerAccount(null)} />}

      <footer className="app-footer">
        Java 21 · Spring Boot · Kafka · PostgreSQL · Redis — see the repo README for architecture &amp; benchmarks.
      </footer>
    </>
  );
}
