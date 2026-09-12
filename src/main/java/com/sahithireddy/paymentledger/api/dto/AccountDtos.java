package com.sahithireddy.paymentledger.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class AccountDtos {

    public record CreateAccountRequest(
        @NotBlank String ownerName,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "currency must be a 3-letter ISO code, e.g. USD") String currency,
        @NotNull @PositiveOrZero BigDecimal initialBalance
    ) {}

    public record AccountResponse(
        UUID id,
        String ownerName,
        String currency,
        BigDecimal balance,
        Instant createdAt
    ) {}

    public record LedgerEntryResponse(
        UUID id,
        UUID accountId,
        UUID paymentId,
        String entryType,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Instant createdAt
    ) {}
}

