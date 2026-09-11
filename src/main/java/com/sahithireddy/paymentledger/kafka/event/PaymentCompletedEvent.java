package com.sahithireddy.paymentledger.kafka.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Published to {@code payments.completed} once the ledger has been updated. */
public record PaymentCompletedEvent(
    UUID paymentId,
    UUID fromAccountId,
    UUID toAccountId,
    BigDecimal amount,
    String currency,
    Instant completedAt
) {}
