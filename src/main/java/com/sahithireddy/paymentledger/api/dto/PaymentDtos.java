package com.sahithireddy.paymentledger.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class PaymentDtos {

    public record CreatePaymentRequest(
        @NotNull UUID fromAccountId,
        @NotNull UUID toAccountId,
        @NotNull @DecimalMin(value = "0.01", message = "amount must be positive") BigDecimal amount,
        @NotNull @Pattern(regexp = "[A-Z]{3}", message = "currency must be a 3-letter ISO code, e.g. USD") String currency
    ) {}

    public record PaymentResponse(
        UUID id,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String currency,
        String status,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
    ) {}
}
