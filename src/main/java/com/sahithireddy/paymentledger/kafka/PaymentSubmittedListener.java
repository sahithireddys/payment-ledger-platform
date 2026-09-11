package com.sahithireddy.paymentledger.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sahithireddy.paymentledger.kafka.event.PaymentSubmittedEvent;
import com.sahithireddy.paymentledger.service.PaymentProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumer group {@code ledger-processor}. Concurrency of 3 matches the
 * topic's partition count, so each partition — and therefore each source
 * account's ordered stream of submissions — is handled by exactly one
 * thread at a time.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentSubmittedListener {

    private final PaymentProcessingService paymentProcessingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "${app.payments.submitted-topic}",
        groupId = "ledger-processor",
        concurrency = "3")
    public void onPaymentSubmitted(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            PaymentSubmittedEvent event = objectMapper.readValue(record.value(), PaymentSubmittedEvent.class);
            paymentProcessingService.process(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing payments.submitted record at offset {}: {}", record.offset(), e.getMessage(), e);
            // Do not ack: DefaultErrorHandler (configured in KafkaConsumerConfig) will
            // retry with backoff, then route to the dead-letter topic if it keeps failing.
            throw e;
        }
    }
}
