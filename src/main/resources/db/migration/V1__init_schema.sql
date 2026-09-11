-- Core schema for the distributed payment processing & double-entry ledger platform.

CREATE TABLE accounts (
    id              UUID PRIMARY KEY,
    owner_name      VARCHAR(255)   NOT NULL,
    currency        VARCHAR(3)     NOT NULL,
    balance         NUMERIC(19,4)  NOT NULL CHECK (balance >= 0),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE TABLE payments (
    id                  UUID PRIMARY KEY,
    from_account_id     UUID           NOT NULL REFERENCES accounts(id),
    to_account_id       UUID           NOT NULL REFERENCES accounts(id),
    amount              NUMERIC(19,4)  NOT NULL CHECK (amount > 0),
    currency            VARCHAR(3)     NOT NULL,
    status              VARCHAR(20)    NOT NULL,
    idempotency_key     VARCHAR(255),
    failure_reason      VARCHAR(255),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_payments_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_from_account ON payments(from_account_id);
CREATE INDEX idx_payments_to_account ON payments(to_account_id);

CREATE TABLE ledger_entries (
    id              UUID PRIMARY KEY,
    account_id      UUID           NOT NULL REFERENCES accounts(id),
    payment_id      UUID           NOT NULL REFERENCES payments(id),
    entry_type      VARCHAR(10)    NOT NULL CHECK (entry_type IN ('DEBIT','CREDIT')),
    amount          NUMERIC(19,4)  NOT NULL CHECK (amount > 0),
    balance_after   NUMERIC(19,4)  NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_ledger_entries_account ON ledger_entries(account_id, created_at DESC);
CREATE INDEX idx_ledger_entries_payment ON ledger_entries(payment_id);

-- Transactional outbox: written in the same DB transaction as the domain change,
-- polled and published to Kafka by a background publisher. Avoids the dual-write
-- problem (DB commit succeeding while the Kafka publish is lost, or vice versa).
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(50)    NOT NULL,
    aggregate_id    UUID           NOT NULL,
    event_type      VARCHAR(50)    NOT NULL,
    topic           VARCHAR(100)   NOT NULL,
    partition_key   VARCHAR(100)   NOT NULL,
    payload         TEXT           NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox_events(created_at) WHERE published_at IS NULL;

-- Guards the Kafka consumer against reprocessing the same payment event twice
-- (broker redelivery after a crash before offset commit, rebalances, etc.).
CREATE TABLE processed_payment_events (
    payment_id      UUID PRIMARY KEY REFERENCES payments(id),
    processed_at    TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE TABLE notifications (
    id              UUID PRIMARY KEY,
    payment_id      UUID           NOT NULL REFERENCES payments(id),
    event_type      VARCHAR(50)    NOT NULL,
    message         VARCHAR(500)   NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);
