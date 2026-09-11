package com.sahithireddy.paymentledger.kafka;

import com.sahithireddy.paymentledger.config.OutboxProperties;
import com.sahithireddy.paymentledger.domain.OutboxEvent;
import com.sahithireddy.paymentledger.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Polls the {@code outbox_events} table and publishes unpublished rows to
 * Kafka. This is the second half of the transactional outbox pattern: the
 * write side ({@code PaymentWriteService}, {@code PaymentProcessingService})
 * only ever writes to Postgres, inside the same transaction as the domain
 * change; this publisher is the only thing that talks to Kafka, and does so
 * outside any application transaction.
 *
 * <p>Delivery semantics are at-least-once: a row is marked published only
 * after the broker acknowledges it, but if the process crashes between the
 * broker ack and the DB commit that records {@code published_at}, the same
 * row is republished on restart. Consumers must therefore be idempotent
 * (see {@code PaymentProcessingService}), which they are.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties outboxProperties;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:100}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> batch = outboxEventRepository.findUnpublishedBatchForUpdate(outboxProperties.getBatchSize());
        if (batch.isEmpty()) {
            return;
        }

        for (OutboxEvent event : batch) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getPartitionKey(), event.getPayload())
                    .get(5, TimeUnit.SECONDS);
                event.setPublishedAt(Instant.now());
                outboxEventRepository.save(event);
            } catch (Exception e) {
                // Leave published_at null; this row will be retried on the next poll.
                // The row-level lock is released when this method's transaction ends,
                // so a stuck broker delays but never blocks the rest of the batch.
                log.error("Failed to publish outbox event {} to topic {} (will retry)",
                    event.getId(), event.getTopic(), e);
            }
        }
    }
}
