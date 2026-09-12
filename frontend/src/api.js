// Thin wrapper around the Payment Ledger Platform REST API.
// The backend is expected at localhost:8080 (its default port); override
// with a .env file (VITE_API_BASE_URL=...) if you run it elsewhere.
const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

async function request(path, options = {}) {
  const { headers, ...rest } = options;
  const res = await fetch(`${BASE_URL}${path}`, {
    ...rest,
    // `rest` is spread first and `headers` built last, so the merged
    // Content-Type survives — spreading `options` (headers included) after
    // this object literal, as an earlier version did, silently overwrites
    // the merge and drops Content-Type, which makes a POST body default to
    // text/plain and get rejected by Spring's message converters.
    headers: { 'Content-Type': 'application/json', ...(headers || {}) },
  });

  if (!res.ok) {
    let message = `${res.status} ${res.statusText}`;
    try {
      const body = await res.json();
      message = body.message || body.error || message;
    } catch {
      // response wasn't JSON (e.g. the backend is unreachable/misconfigured) — keep the status text
    }
    throw new Error(message);
  }

  if (res.status === 204) return null;
  return res.json();
}

export const api = {
  listAccounts: () => request('/api/v1/accounts'),

  getAccount: (id) => request(`/api/v1/accounts/${id}`),

  createAccount: (body) =>
    request('/api/v1/accounts', { method: 'POST', body: JSON.stringify(body) }),

  getLedger: (accountId, { page = 0, size = 20 } = {}) =>
    request(`/api/v1/accounts/${accountId}/ledger?page=${page}&size=${size}`),

  listPayments: ({ page = 0, size = 20 } = {}) =>
    request(`/api/v1/payments?page=${page}&size=${size}`),

  getPayment: (id) => request(`/api/v1/payments/${id}`),

  submitPayment: (body, idempotencyKey) =>
    request('/api/v1/payments', {
      method: 'POST',
      headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {},
      body: JSON.stringify(body),
    }),
};

// A payment is COMPLETED/FAILED almost immediately in practice (the whole
// point of the async pipeline is that it's fast), but it's still genuinely
// asynchronous — the API returns PENDING before Kafka has processed it. This
// polls the payment until it leaves PENDING, or gives up after `timeoutMs`.
export async function pollPaymentUntilSettled(paymentId, { intervalMs = 400, timeoutMs = 10000 } = {}) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const payment = await api.getPayment(paymentId);
    if (payment.status !== 'PENDING') return payment;
    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }
  return api.getPayment(paymentId);
}
