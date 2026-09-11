package com.sahithireddy.paymentledger.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sahithireddy.paymentledger.config.PaymentProperties;
import com.sahithireddy.paymentledger.domain.*;
import com.sahithireddy.paymentledger.kafka.event.EventTypes;
import com.sahithireddy.paymentledger.kafka.event.PaymentCompletedEvent;
import com.sahithireddy.paymentledger.kafka.event.PaymentFailedEvent;
import com.sahithireddy.paymentledger.kafka.event.PaymentSubmittedEvent;
import com.sahithireddy.paymentledger.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Applies a submitted payment to the ledger. Invoked by the Kafka listener
 * for {@code payments.submitted}; every public method here runs inside one
 * database transaction that is either fully applied or fully rolled back —
 * there is no window where a balance is debited without a matching credit,
 * or where a ledger entry exists without an updated balance.
 *
 * <p>Concurrency safety: both accounts involved are locked with
 * {@code SELECT ... FOR UPDATE}, always in ascending id order. Two transfers
 * that both touch accounts A and B — even in opposite directions — always
 * acquire their locks in the same order, so neither can deadlock waiting on
 * the other.
 *
 * <p>Idempotency: before touching any balance, this checks
 * {@code processed_payment_events} for the payment id. Kafka's at-least-once
 * delivery means the same message can arrive more than once (a rebalance, a
 * crash between processing and offset commit); the insert into that table
 * happens in the same transaction as the ledger update, so a redelivered
 * message is a guaranteed no-op rather than a double-spend.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentProcessingService {

    private final AccountRepository accountRepository;
    private final PaymentRepository paymentRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProcessedPaymentEventRepository processedPaymentEventRepository;
    private final AccountService accountService;
    private final PaymentProperties paymentProperties;
    private final ObjectMapper objectMapper;

    @Transactional
    public void process(PaymentSubmittedEvent event) {
        if (processedPaymentEventRepository.existsById(event.paymentId())) {
            log.info("Payment {} already processed, skipping duplicate delivery", event.paymentId());
            return;
        }

        Payment payment = paymentRepository.findById(event.paymentId())
            .orElseThrow(() -> new IllegalStateException("Outbox referenced unknown payment " + event.paymentId()));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("Payment {} is already in terminal state {}, skipping", payment.getId(), payment.getStatus());
            return;
        }

        UUID firstLockId = event.fromAccountId().compareTo(event.toAccountId()) <= 0
            ? event.fromAccountId() : event.toAccountId();
        UUID secondLockId = firstLockId.equals(event.fromAccountId()) ? event.toAccountId() : event.fromAccountId();

        Account first = accountRepository.findByIdForUpdate(firstLockId)
            .orElseThrow(() -> new IllegalStateException("Account " + firstLockId + " referenced by a payment no longer exists"));
        Account second = accountRepository.findByIdForUpdate(secondLockId)
            .orElseThrow(() -> new IllegalStateException("Account " + secondLockId + " referenced by a payment no longer exists"));

        Account from = first.getId().equals(event.fromAccountId()) ? first : second;
        Account to = first.getId().equals(event.toAccountId()) ? first : second;

        if (from.getBalance().compareTo(event.amount()) < 0) {
            failPayment(payment, "INSUFFICIENT_FUNDS");
            return;
        }

        applyTransfer(payment, from, to);
    }

    private void applyTransfer(Payment payment, Account from, Account to) {
        from.debit(payment.getAmount());
        to.credit(payment.getAmount());
        accountRepository.save(from);
        accountRepository.save(to);

        Instant now = Instant.now();
        ledgerEntryRepository.save(LedgerEntry.builder()
            .id(UUID.randomUUID())
            .accountId(from.getId())
            .paymentId(payment.getId())
            .entryType(EntryType.DEBIT)
            .amount(payment.getAmount())
            .balanceAfter(from.getBalance())
            .build());
        ledgerEntryRepository.save(LedgerEntry.builder()
            .id(UUID.randomUUID())
            .accountId(to.getId())
            .paymentId(payment.getId())
            .entryType(EntryType.CREDIT)
            .amount(payment.getAmount())
            .balanceAfter(to.getBalance())
            .build());

        payment.setStatus(PaymentStatus.COMPLETED);
        paymentRepository.save(payment);

        processedPaymentEventRepository.save(new ProcessedPaymentEvent(payment.getId()));

        PaymentCompletedEvent completed = new PaymentCompletedEvent(
            payment.getId(), from.getId(), to.getId(), payment.getAmount(), payment.getCurrency(), now);
        enqueueOutbox(payment.getId(), EventTypes.PAYMENT_COMPLETED, paymentProperties.getCompletedTopic(),
            from.getId().toString(), completed);

        accountService.invalidateBalanceCache(from.getId());
        accountService.invalidateBalanceCache(to.getId());

        log.info("Payment {} completed: {} -> {} amount {}", payment.getId(), from.getId(), to.getId(), payment.getAmount());
    }

    private void failPayment(Payment payment, String reason) {
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        paymentRepository.save(payment);

        processedPaymentEventRepository.save(new ProcessedPaymentEvent(payment.getId()));

        PaymentFailedEvent failed = new PaymentFailedEvent(
            payment.getId(), payment.getFromAccountId(), payment.getToAccountId(),
            payment.getAmount(), payment.getCurrency(), reason, Instant.now());
        enqueueOutbox(payment.getId(), EventTypes.PAYMENT_FAILED, paymentProperties.getFailedTopic(),
            payment.getFromAccountId().toString(), failed);

        log.warn("Payment {} failed: {}", payment.getId(), reason);
    }

    private void enqueueOutbox(UUID paymentId, String eventType, String topic, String partitionKey, Object payload) {
        outboxEventRepository.save(OutboxEvent.builder()
            .id(UUID.randomUUID())
            .aggregateType("Payment")
            .aggregateId(paymentId)
            .eventType(eventType)
            .topic(topic)
            .partitionKey(partitionKey)
            .payload(writeJson(payload))
            .build());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox event payload", e);
        }
    }
}
