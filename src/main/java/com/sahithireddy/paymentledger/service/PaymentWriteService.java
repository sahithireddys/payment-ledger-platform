package com.sahithireddy.paymentledger.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sahithireddy.paymentledger.api.dto.PaymentDtos.CreatePaymentRequest;
import com.sahithireddy.paymentledger.api.dto.PaymentDtos.PaymentResponse;
import com.sahithireddy.paymentledger.config.PaymentProperties;
import com.sahithireddy.paymentledger.domain.Account;
import com.sahithireddy.paymentledger.domain.OutboxEvent;
import com.sahithireddy.paymentledger.domain.Payment;
import com.sahithireddy.paymentledger.domain.PaymentStatus;
import com.sahithireddy.paymentledger.exception.AccountNotFoundException;
import com.sahithireddy.paymentledger.exception.CurrencyMismatchException;
import com.sahithireddy.paymentledger.kafka.event.EventTypes;
import com.sahithireddy.paymentledger.kafka.event.PaymentSubmittedEvent;
import com.sahithireddy.paymentledger.repository.AccountRepository;
import com.sahithireddy.paymentledger.repository.OutboxEventRepository;
import com.sahithireddy.paymentledger.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Kept as its own Spring bean (rather than a method on
 * {@link PaymentSubmissionService}) purely so that {@code @Transactional}
 * is honored: Spring's proxy-based transactions only intercept calls that
 * arrive through the bean proxy, and a method calling another method on
 * {@code this} bypasses that proxy entirely (the classic "self-invocation"
 * pitfall). Splitting the transactional write into a separate, injected
 * collaborator sidesteps that.
 */
@Service
@RequiredArgsConstructor
public class PaymentWriteService {

    private final AccountRepository accountRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentProperties paymentProperties;
    private final ObjectMapper objectMapper;

    @Transactional
    public PaymentResponse createPaymentAndEnqueue(CreatePaymentRequest request, String idempotencyKey) {
        Account from = accountRepository.findById(request.fromAccountId())
            .orElseThrow(() -> new AccountNotFoundException(request.fromAccountId()));
        Account to = accountRepository.findById(request.toAccountId())
            .orElseThrow(() -> new AccountNotFoundException(request.toAccountId()));

        if (!from.getCurrency().equals(request.currency()) || !to.getCurrency().equals(request.currency())) {
            throw new CurrencyMismatchException(
                "Payment currency " + request.currency() + " does not match account currency");
        }

        Payment payment = Payment.builder()
            .id(UUID.randomUUID())
            .fromAccountId(from.getId())
            .toAccountId(to.getId())
            .amount(request.amount())
            .currency(request.currency())
            .status(PaymentStatus.PENDING)
            .idempotencyKey(idempotencyKey)
            .build();
        paymentRepository.save(payment);

        PaymentSubmittedEvent event = new PaymentSubmittedEvent(
            payment.getId(), from.getId(), to.getId(), payment.getAmount(), payment.getCurrency(), Instant.now());

        OutboxEvent outboxEvent = OutboxEvent.builder()
            .id(UUID.randomUUID())
            .aggregateType("Payment")
            .aggregateId(payment.getId())
            .eventType(EventTypes.PAYMENT_SUBMITTED)
            .topic(paymentProperties.getSubmittedTopic())
            // Keying by the source account keeps all of one account's outgoing
            // payments in the same Kafka partition, so they are processed in
            // the order they were submitted.
            .partitionKey(from.getId().toString())
            .payload(writeJson(event))
            .build();
        outboxEventRepository.save(outboxEvent);

        return toResponse(payment);
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getFromAccountId(), payment.getToAccountId(),
            payment.getAmount(), payment.getCurrency(), payment.getStatus().name(), payment.getFailureReason(),
            payment.getCreatedAt(), payment.getUpdatedAt());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox event payload", e);
        }
    }
}
