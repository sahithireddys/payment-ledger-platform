package com.sahithireddy.paymentledger.service;

import com.sahithireddy.paymentledger.api.dto.PaymentDtos.CreatePaymentRequest;
import com.sahithireddy.paymentledger.api.dto.PaymentDtos.PaymentResponse;
import com.sahithireddy.paymentledger.domain.Payment;
import com.sahithireddy.paymentledger.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Handles the synchronous half of a payment: an idempotency check, then a
 * single, cheap local transaction (delegated to {@link PaymentWriteService})
 * that records intent (Payment=PENDING) and queues an event (OutboxEvent)
 * for asynchronous ledger processing. Deliberately does NOT touch account
 * balances here — that happens later, off the request path, in
 * {@link PaymentProcessingService}. This is what keeps submission latency
 * low and largely independent of how backed up the Kafka consumer is.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentSubmissionService {

    private final PaymentRepository paymentRepository;
    private final PaymentWriteService paymentWriteService;
    private final IdempotencyService idempotencyService;

    public PaymentResponse submit(CreatePaymentRequest request, String idempotencyKey) {
        if (idempotencyKey != null) {
            var cached = idempotencyService.getCachedResponse(idempotencyKey);
            if (cached.isPresent()) {
                log.debug("Idempotent replay for key {}", idempotencyKey);
                return cached.get();
            }
        }

        PaymentResponse response;
        try {
            response = paymentWriteService.createPaymentAndEnqueue(request, idempotencyKey);
        } catch (DataIntegrityViolationException e) {
            // Two concurrent requests raced with the same brand-new Idempotency-Key;
            // the unique constraint on payments.idempotency_key let exactly one win.
            // Return the winner's payment instead of failing the loser's request.
            if (idempotencyKey != null) {
                Payment existing = paymentRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> e);
                response = toResponse(existing);
            } else {
                throw e;
            }
        }

        if (idempotencyKey != null) {
            idempotencyService.cacheResponse(idempotencyKey, response);
        }
        return response;
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getFromAccountId(), payment.getToAccountId(),
            payment.getAmount(), payment.getCurrency(), payment.getStatus().name(), payment.getFailureReason(),
            payment.getCreatedAt(), payment.getUpdatedAt());
    }
}
