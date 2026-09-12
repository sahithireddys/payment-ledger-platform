import { useEffect, useState } from 'react';
import { api } from '../api.js';

export default function LedgerDrawer({ account, onClose }) {
  const [entries, setEntries] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    api
      .getLedger(account.id, { page: 0, size: 25 })
      .then((page) => {
        if (!cancelled) setEntries(page.content);
      })
      .catch((err) => {
        if (!cancelled) setError(err.message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [account.id]);

  return (
    <div className="overlay" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>{account.ownerName}'s ledger</h3>
          <button className="link" onClick={onClose}>
            Close
          </button>
        </div>
        <div className="text-muted mono" style={{ fontSize: 11, marginBottom: 12 }}>
          {account.id}
        </div>

        <div className="modal-body">
          {error && <div className="error-banner">{error}</div>}
          {loading ? (
            <div className="empty-state">Loading ledger…</div>
          ) : entries.length === 0 ? (
            <div className="empty-state">No ledger entries yet.</div>
          ) : (
            <table className="activity">
              <thead>
                <tr>
                  <th>Type</th>
                  <th>Amount</th>
                  <th>Balance after</th>
                  <th>When</th>
                </tr>
              </thead>
              <tbody>
                {entries.map((e) => (
                  <tr key={e.id}>
                    <td>
                      <span className={`pill ${e.entryType.toLowerCase()}`}>{e.entryType}</span>
                    </td>
                    <td className="mono">{Number(e.amount).toFixed(2)}</td>
                    <td className="mono">{Number(e.balanceAfter).toFixed(2)}</td>
                    <td className="text-muted">{new Date(e.createdAt).toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
}
