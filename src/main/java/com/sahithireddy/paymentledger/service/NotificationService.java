package com.sahithireddy.paymentledger.service;

import com.sahithireddy.paymentledger.domain.Notification;
import com.sahithireddy.paymentledger.kafka.event.EventTypes;
import com.sahithireddy.paymentledger.kafka.event.PaymentCompletedEvent;
import com.sahithireddy.paymentledger.kafka.event.PaymentFailedEvent;
import com.sahithireddy.paymentledger.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Stands in for a downstream notification/webhook service. Subscribes to
 * {@code payments.completed} / {@code payments.failed} under its own
 * consumer group ({@code notification-service}), completely independent of
 * the {@code ledger-processor} group that actually posts the ledger. Kafka
 * fans the same events out to both groups; each scales, fails over, and
 * replays independently of the other.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public void handleCompleted(PaymentCompletedEvent event) {
        record(event.paymentId(), EventTypes.PAYMENT_COMPLETED,
            "Payment of " + event.amount() + " " + event.currency() + " completed successfully.");
    }

    @Transactional
    public void handleFailed(PaymentFailedEvent event) {
        record(event.paymentId(), EventTypes.PAYMENT_FAILED,
            "Payment of " + event.amount() + " " + event.currency() + " failed: " + event.reason());
    }

    private void record(UUID paymentId, String eventType, String message) {
        notificationRepository.save(Notification.builder()
            .id(UUID.randomUUID())
            .paymentId(paymentId)
            .eventType(eventType)
            .message(message)
            .build());
        log.info("Notification recorded for payment {}: {}", paymentId, message);
    }
}
