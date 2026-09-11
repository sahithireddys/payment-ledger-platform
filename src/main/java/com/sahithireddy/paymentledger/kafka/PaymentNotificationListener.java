package com.sahithireddy.paymentledger.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sahithireddy.paymentledger.kafka.event.PaymentCompletedEvent;
import com.sahithireddy.paymentledger.kafka.event.PaymentFailedEvent;
import com.sahithireddy.paymentledger.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumer group {@code notification-service} — reads the same
 * {@code payments.completed} / {@code payments.failed} topics as the ledger
 * processor but as a fully independent group, so it gets its own copy of
 * every event regardless of how the ledger-processing group is doing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentNotificationListener {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "${app.payments.completed-topic}",
        groupId = "notification-service")
    public void onPaymentCompleted(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            PaymentCompletedEvent event = objectMapper.readValue(record.value(), PaymentCompletedEvent.class);
            notificationService.handleCompleted(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing payments.completed record at offset {}: {}", record.offset(), e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(
        topics = "${app.payments.failed-topic}",
        groupId = "notification-service")
    public void onPaymentFailed(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            PaymentFailedEvent event = objectMapper.readValue(record.value(), PaymentFailedEvent.class);
            notificationService.handleFailed(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing payments.failed record at offset {}: {}", record.offset(), e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
