import { useState } from 'react';

function formatMoney(value, currency) {
  const n = Number(value);
  return `${n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ${currency}`;
}

export default function AccountsPanel({ accounts, loading, onSelectAccount, onCreateAccount }) {
  const [showForm, setShowForm] = useState(false);
  const [ownerName, setOwnerName] = useState('');
  const [currency, setCurrency] = useState('USD');
  const [initialBalance, setInitialBalance] = useState('1000.00');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  async function handleSubmit(e) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await onCreateAccount({ ownerName, currency, initialBalance: Number(initialBalance) });
      setOwnerName('');
      setInitialBalance('1000.00');
      setShowForm(false);
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="card">
      <div className="card-header">
        <h2>Accounts ({accounts.length})</h2>
        <button className="link" onClick={() => setShowForm((v) => !v)}>
          {showForm ? 'Cancel' : '+ New account'}
        </button>
      </div>

      {showForm && (
        <form className="stacked" onSubmit={handleSubmit} style={{ marginBottom: 16 }}>
          {error && <div className="error-banner">{error}</div>}
          <div className="field">
            <label>Owner name</label>
            <input value={ownerName} onChange={(e) => setOwnerName(e.target.value)} required placeholder="e.g. Alice" />
          </div>
          <div className="field-row">
            <div className="field">
              <label>Currency</label>
              <input
                value={currency}
                onChange={(e) => setCurrency(e.target.value.toUpperCase())}
                maxLength={3}
                pattern="[A-Z]{3}"
                required
              />
            </div>
            <div className="field">
              <label>Initial balance</label>
              <input
                type="number"
                min="0"
                step="0.01"
                value={initialBalance}
                onChange={(e) => setInitialBalance(e.target.value)}
                required
              />
            </div>
          </div>
          <button className="primary" type="submit" disabled={submitting}>
            {submitting ? 'Creating…' : 'Create account'}
          </button>
        </form>
      )}

      {loading && accounts.length === 0 ? (
        <div className="empty-state">Loading accounts…</div>
      ) : accounts.length === 0 ? (
        <div className="empty-state">No accounts yet — create one above to get started.</div>
      ) : (
        <div className="account-list">
          {accounts.map((a) => (
            <button key={a.id} className="account-row" onClick={() => onSelectAccount(a)} title="View ledger">
              <span>
                <div className="owner">{a.ownerName}</div>
                <div className="id">{a.id}</div>
              </span>
              <span className="balance">
                {formatMoney(a.balance, a.currency)}
              </span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
