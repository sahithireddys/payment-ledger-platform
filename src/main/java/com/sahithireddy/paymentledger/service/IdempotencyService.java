package com.sahithireddy.paymentledger.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sahithireddy.paymentledger.api.dto.PaymentDtos.PaymentResponse;
import com.sahithireddy.paymentledger.config.PaymentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Guards the payment submission endpoint against duplicate side effects when
 * a client retries a request (network timeout, load balancer retry, a user
 * double-clicking "Pay"). The Idempotency-Key header is the caller-supplied
 * de-duplication token: the first request with a given key does the real
 * work and caches its response; every subsequent request with that same key,
 * within the TTL, gets the original cached response back without creating a
 * second payment.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private static final String KEY_PREFIX = "idempotency:payment:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentProperties paymentProperties;

    public Optional<PaymentResponse> getCachedResponse(String idempotencyKey) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + idempotencyKey);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, PaymentResponse.class));
        } catch (Exception e) {
            log.warn("Failed to deserialize cached idempotent response for key {}", idempotencyKey, e);
            return Optional.empty();
        }
    }

    public void cacheResponse(String idempotencyKey, PaymentResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(
                KEY_PREFIX + idempotencyKey,
                json,
                Duration.ofHours(paymentProperties.getIdempotencyTtlHours()));
        } catch (Exception e) {
            // Caching is a best-effort optimization; failing to cache must never fail the request.
            log.warn("Failed to cache idempotent response for key {}", idempotencyKey, e);
        }
    }
}
