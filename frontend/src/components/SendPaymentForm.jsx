import { useState } from 'react';
import StatusPill from './StatusPill.jsx';

function shortId(id) {
  return id ? `${id.slice(0, 8)}…` : '';
}

export default function SendPaymentForm({ accounts, onSubmitPayment }) {
  const [fromAccountId, setFromAccountId] = useState('');
  const [toAccountId, setToAccountId] = useState('');
  const [amount, setAmount] = useState('25.00');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [result, setResult] = useState(null); // { payment, settled }

  const fromAccount = accounts.find((a) => a.id === fromAccountId);

  async function handleSubmit(e) {
    e.preventDefault();
    if (!fromAccountId || !toAccountId) {
      setError('Choose both a source and destination account.');
      return;
    }
    if (fromAccountId === toAccountId) {
      setError('Source and destination accounts must be different.');
      return;
    }

    setSubmitting(true);
    setError(null);
    setResult(null);
    try {
      const { submitted, settle } = onSubmitPayment({
        fromAccountId,
        toAccountId,
        amount: Number(amount),
        currency: fromAccount?.currency || 'USD',
      });

      const submittedPayment = await submitted;
      setResult({ payment: submittedPayment, settled: false });

      const settledPayment = await settle;
      setResult({ payment: settledPayment, settled: true });
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="card">
      <div className="card-header">
        <h2>Send a payment</h2>
        <span className="hint">Async — watch it settle below</span>
      </div>

      <form className="stacked" onSubmit={handleSubmit}>
        {error && <div className="error-banner">{error}</div>}

        <div className="field-row">
          <div className="field">
            <label>From account</label>
            <select value={fromAccountId} onChange={(e) => setFromAccountId(e.target.value)} required>
              <option value="">Select…</option>
              {accounts.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.ownerName} — {Number(a.balance).toFixed(2)} {a.currency}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label>To account</label>
            <select value={toAccountId} onChange={(e) => setToAccountId(e.target.value)} required>
              <option value="">Select…</option>
              {accounts
                .filter((a) => a.id !== fromAccountId)
                .map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.ownerName} — {Number(a.balance).toFixed(2)} {a.currency}
                  </option>
                ))}
            </select>
          </div>
        </div>

        <div className="field">
          <label>Amount {fromAccount ? `(${fromAccount.currency})` : ''}</label>
          <input type="number" min="0.01" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} required />
        </div>

        <button className="primary" type="submit" disabled={submitting || accounts.length < 2}>
          {submitting && !result?.settled ? 'Submitting…' : 'Send payment'}
        </button>
        {accounts.length < 2 && <span className="hint">Create at least two accounts first.</span>}
      </form>

      {result && (
        <div className="result-box">
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
            <StatusPill status={result.payment.status} />
            {!result.settled && <span className="spinner" />}
            <span className="mono text-muted" style={{ fontSize: 12 }}>
              payment {shortId(result.payment.id)}
            </span>
          </div>
          <div className="text-muted" style={{ fontSize: 12 }}>
            {result.settled
              ? result.payment.status === 'COMPLETED'
                ? 'Ledger updated — balances below now reflect this transfer.'
                : `Failed: ${result.payment.failureReason || 'unknown reason'}`
              : 'Submitted to the outbox — waiting for the Kafka consumer to apply it to the ledger…'}
          </div>
        </div>
      )}
    </div>
  );
}
