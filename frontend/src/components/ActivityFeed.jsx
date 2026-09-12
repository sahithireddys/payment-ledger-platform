import StatusPill from './StatusPill.jsx';

function shortId(id) {
  return id ? `${id.slice(0, 8)}…` : '';
}

function relativeTime(iso) {
  const seconds = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000));
  if (seconds < 5) return 'just now';
  if (seconds < 60) return `${seconds}s ago`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  return new Date(iso).toLocaleTimeString();
}

export default function ActivityFeed({ payments, accountsById, loading }) {
  return (
    <div className="card">
      <div className="card-header">
        <h2>Recent activity</h2>
        <span className="hint">Auto-refreshes every few seconds</span>
      </div>

      {loading && payments.length === 0 ? (
        <div className="empty-state">Loading…</div>
      ) : payments.length === 0 ? (
        <div className="empty-state">No payments submitted yet.</div>
      ) : (
        <table className="activity">
          <thead>
            <tr>
              <th>From</th>
              <th>To</th>
              <th>Amount</th>
              <th>Status</th>
              <th>When</th>
            </tr>
          </thead>
          <tbody>
            {payments.map((p) => (
              <tr key={p.id}>
                <td>{accountsById[p.fromAccountId]?.ownerName || <span className="mono">{shortId(p.fromAccountId)}</span>}</td>
                <td>{accountsById[p.toAccountId]?.ownerName || <span className="mono">{shortId(p.toAccountId)}</span>}</td>
                <td className="mono">
                  {Number(p.amount).toFixed(2)} {p.currency}
                </td>
                <td>
                  <StatusPill status={p.status} />
                </td>
                <td className="text-muted">{relativeTime(p.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
