# Payment Ledger Platform — Frontend

A small React + Vite dashboard for the [Payment Ledger Platform](../README.md) API: create accounts, send payments, watch them settle asynchronously, and browse each account's ledger — all against the real backend, not mock data.

Deliberately minimal dependencies (React, Vite, one plugin) — no state library, no CSS framework, no router. It's a single page: everything is `fetch` calls to the Spring Boot API plus React state.

## Running it

Requires the backend already running (see the [root README](../README.md#running-it-locally)) — `docker compose up -d` and `mvn spring-boot:run` from the project root, in a different terminal tab.

```bash
cd frontend
npm install
npm run dev
```

Then open the URL Vite prints (defaults to `http://localhost:5173`).

If your backend isn't on `localhost:8080`, copy `.env.example` to `.env` and set `VITE_API_BASE_URL`.

## What it does

- **Accounts panel** — lists every account and its live balance; create new ones inline. Click an account to see its full ledger (debit/credit history) in a modal.
- **Send a payment** — pick a source and destination account and an amount, submit, and watch the status pill go `PENDING` → `COMPLETED` (or `FAILED`) in real time as the Kafka consumer processes it. This is the async pipeline described in the backend's `docs/architecture.md`, visible from the outside.
- **Recent activity feed** — the last 15 payments across all accounts, polling every few seconds, so a payment submitted from a second browser tab (or via `curl`, or the k6 load test) shows up here too.
- A small connection badge in the header shows whether the backend is reachable (pings `/actuator/health`).

## Notes

- Idempotency: every payment submission generates a fresh `Idempotency-Key` client-side, matching how the backend expects retries to be made safe.
- This intentionally talks straight to the backend from the browser (no server-side rendering, no proxy) — that's what the `WebConfig` CORS setup on the backend exists for.
