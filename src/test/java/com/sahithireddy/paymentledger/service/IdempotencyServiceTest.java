package com.sahithireddy.paymentledger.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sahithireddy.paymentledger.api.dto.PaymentDtos.PaymentResponse;
import com.sahithireddy.paymentledger.config.PaymentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class IdempotencyServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private IdempotencyService service;
    private final Map<String, String> fakeStore = new HashMap<>();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenAnswer(inv -> fakeStore.get(inv.getArgument(0)));
        org.mockito.Mockito.doAnswer(inv -> {
            fakeStore.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        service = new IdempotencyService(redisTemplate, objectMapper, new PaymentProperties());
    }

    @Test
    void cacheMiss_returnsEmpty() {
        assertThat(service.getCachedResponse("unknown-key")).isEmpty();
    }

    @Test
    void cachedResponse_isReturnedOnReplay() {
        PaymentResponse response = new PaymentResponse(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            new BigDecimal("10.00"), "USD", "PENDING", null, Instant.now(), Instant.now());

        service.cacheResponse("key-123", response);
        Optional<PaymentResponse> replayed = service.getCachedResponse("key-123");

        assertThat(replayed).isPresent();
        assertThat(replayed.get().id()).isEqualTo(response.id());
        assertThat(replayed.get().amount()).isEqualByComparingTo(response.amount());
    }
}
