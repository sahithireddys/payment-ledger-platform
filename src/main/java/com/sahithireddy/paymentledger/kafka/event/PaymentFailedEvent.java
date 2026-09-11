package com.sahithireddy.paymentledger.kafka.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Published to {@code payments.failed} when a payment cannot be completed (e.g. insufficient funds). */
public record PaymentFailedEvent(
    UUID paymentId,
    UUID fromAccountId,
    UUID toAccountId,
    BigDecimal amount,
    String currency,
    String reason,
    Instant failedAt
) {}
