# Payment Ledger Platform

A distributed payment processing and double-entry ledger service. Submitting
a payment is a fast, synchronous API call; actually moving money happens
asynchronously through Kafka, with a transactional outbox, idempotent
consumers, and deadlock-free concurrency control keeping it correct under
concurrent load.

Built to be run and load-tested for real — see [Benchmark results](#benchmark-results).

## Stack

Java 21 · Spring Boot 3 · Apache Kafka · PostgreSQL · Redis · Flyway ·
Testcontainers · k6 · Docker Compose

## Architecture

See [`docs/architecture.md`](docs/architecture.md) for the full request flow
and the reasoning behind each design decision (transactional outbox,
idempotent consumers, deadlock-free lock ordering, partition-based ordering
guarantees). Short version:

1. `POST /api/v1/payments` validates and durably records the payment as
   `PENDING`, plus an outbox row, in one fast local transaction. Returns
   `202 Accepted` immediately.
2. A background publisher polls the outbox and publishes to
   `payments.submitted`, keyed by the source account (preserves per-account
   ordering).
3. A Kafka consumer (`ledger-processor` group) locks both accounts in a
   consistent order, applies the debit/credit, writes two ledger entries,
   and marks the payment `COMPLETED` or `FAILED` — all in one transaction,
   guarded against duplicate delivery.
4. A second, independent consumer group (`notification-service`) reads the
   same completion/failure events to simulate a downstream notifier.

## Running it locally

Requires Docker and Java 21 + Maven.

```bash
# 1. Start Postgres, Kafka (KRaft mode), Redis, and Kafka UI.
docker compose up -d

# 2. Run the app (applies Flyway migrations automatically on boot).
mvn spring-boot:run

# API docs: http://localhost:8080/swagger-ui.html
# Kafka UI: http://localhost:8081
```

### Try it

```bash
# Create two accounts.
ALICE=$(curl -s -X POST localhost:8080/api/v1/accounts \
  -H 'Content-Type: application/json' \
  -d '{"ownerName":"Alice","currency":"USD","initialBalance":500.00}' | jq -r .id)

BOB=$(curl -s -X POST localhost:8080/api/v1/accounts \
  -H 'Content-Type: application/json' \
  -d '{"ownerName":"Bob","currency":"USD","initialBalance":100.00}' | jq -r .id)

# Submit a payment (idempotency key makes retries safe).
curl -s -X POST localhost:8080/api/v1/payments \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d "{\"fromAccountId\":\"$ALICE\",\"toAccountId\":\"$BOB\",\"amount\":75.00,\"currency\":\"USD\"}"

# Poll status (processing is asynchronous — this usually flips to COMPLETED
# within a few hundred ms).
curl -s localhost:8080/api/v1/payments/<paymentId>

# See the ledger.
curl -s "localhost:8080/api/v1/accounts/$ALICE/ledger"
```

## Frontend

A small React + Vite dashboard lives in [`frontend/`](frontend/) — create accounts, send payments, and watch
them settle asynchronously against this backend in real time. See
[`frontend/README.md`](frontend/README.md) to run it (`cd frontend && npm install && npm run dev`, backend must
already be running).

## Testing

```bash
mvn clean verify
```

Runs unit tests (Mockito) covering the ledger-processing logic — including
lock-ordering and idempotent-replay behavior — plus a Testcontainers
integration suite that spins up real Postgres and Kafka containers and
exercises the full HTTP → outbox → Kafka → consumer → ledger pipeline,
including a test that fires 25 concurrent payments from one account and
asserts the final balance reflects every single one (no lost updates).

CI (`.github/workflows/ci.yml`) runs the same `mvn clean verify` on every
push, using GitHub-hosted runners' built-in Docker for Testcontainers.

## Load testing

```bash
./loadtest/seed-accounts.sh 50
k6 run loadtest/submit-payments.js
```

See [`loadtest/README.md`](loadtest/README.md) for details and how to
adjust the target rate/duration.

## Benchmark results

Measured with `k6 run loadtest/submit-payments.js` (50 seeded accounts,
constant-arrival-rate at 60 req/s for 2 minutes):

| Metric | Result |
|---|---|
| Sustained throughput | **3,600 transactions/min** (7,200 requests in 120.0s) |
| p95 submission latency | **12.6 ms** |
| Failed request rate | **0.00%** |
| Test configuration | `RATE=60`, `DURATION=2m`, 50 seeded accounts |

Environment: MacBook Air (Apple Silicon), Docker Desktop, all four
containers (Postgres, Kafka, Redis, Kafka UI) running locally alongside the
app on one machine.

For context, submission latency stays this low because `POST /payments`
never waits on Kafka or the ledger consumer — it does one small local
transaction (insert `PENDING` payment + outbox row) and returns. The actual
debit/credit happens asynchronously; this benchmark measures the
synchronous half of the pipeline, which is also the half that matters for a
caller's perceived latency.

## Design decisions worth knowing for an interview

- **Why an outbox instead of publishing to Kafka directly in the request?**
  Publishing directly makes the DB commit and the Kafka publish two separate
  non-atomic operations. If the process crashes between them, the payment
  is stuck with no event ever published. The outbox row is written in the
  same transaction as the payment, so publishing becomes "is there an
  unpublished row?" — a question a background process can always eventually
  answer correctly, even after a crash.
- **Why pessimistic locking (`SELECT ... FOR UPDATE`) instead of optimistic
  locking (`@Version`)?** Under load, many payments contend for the same
  popular accounts. Optimistic locking would mean most of those retry after
  a version conflict, wasting the work already done; pessimistic locking
  with consistent lock ordering serializes access safely without retries or
  deadlocks.
- **Why key Kafka messages by account id?** Kafka only orders messages
  within a partition. Keying by the source account guarantees that one
  account's payments are processed in submission order, without requiring a
  single partition (and therefore a single-threaded bottleneck) for the
  whole system.

## Project structure

```
src/main/java/com/sahithireddy/paymentledger/
  api/            REST controllers, DTOs, global exception handling
  domain/         JPA entities
  repository/     Spring Data repositories (including locking queries)
  service/        Business logic (submission, processing, idempotency, accounts)
  kafka/          Outbox publisher, listeners, event payloads
  config/         Kafka topics, consumer error handling, OpenAPI, app properties
src/main/resources/db/migration/   Flyway schema + seed data
src/test/                          Unit tests + Testcontainers integration tests
loadtest/                          k6 load test + account-seeding script
docs/architecture.md               Full request flow + design rationale
frontend/                          React + Vite dashboard (optional UI, see frontend/README.md)
```
