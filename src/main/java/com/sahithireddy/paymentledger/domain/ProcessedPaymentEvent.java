package com.sahithireddy.paymentledger.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Idempotency guard for the ledger-processing Kafka consumer. Inserting a
 * row here is the atomic "have I already applied this payment?" check: it
 * happens in the same transaction as the ledger update, so a redelivered
 * Kafka message (broker retry, consumer rebalance, crash before offset
 * commit) can never be applied twice.
 */
@Entity
@Table(name = "processed_payment_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedPaymentEvent {

    @Id
    @Column(name = "payment_id")
    private UUID paymentId;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    public ProcessedPaymentEvent(UUID paymentId) {
        this.paymentId = paymentId;
    }
}
