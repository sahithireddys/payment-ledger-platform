package com.sahithireddy.paymentledger.kafka.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Published to {@code payments.submitted} when a payment is accepted for processing. */
public record PaymentSubmittedEvent(
    UUID paymentId,
    UUID fromAccountId,
    UUID toAccountId,
    BigDecimal amount,
    String currency,
    Instant submittedAt
) {}
