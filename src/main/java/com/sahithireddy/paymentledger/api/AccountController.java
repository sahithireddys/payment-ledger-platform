package com.sahithireddy.paymentledger.api;

import com.sahithireddy.paymentledger.api.dto.AccountDtos.AccountResponse;
import com.sahithireddy.paymentledger.api.dto.AccountDtos.CreateAccountRequest;
import com.sahithireddy.paymentledger.api.dto.AccountDtos.LedgerEntryResponse;
import com.sahithireddy.paymentledger.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @Operation(summary = "Create an account")
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(request));
    }

    @Operation(summary = "Get an account's current balance",
        description = "Balance is served from a short-TTL Redis cache (cache-aside), falling back to Postgres on a miss.")
    @GetMapping("/{accountId}")
    public AccountResponse getAccount(@PathVariable UUID accountId) {
        return accountService.getAccount(accountId);
    }

    @Operation(summary = "List an account's ledger entries, most recent first")
    @GetMapping("/{accountId}/ledger")
    public Page<LedgerEntryResponse> getLedger(
        @PathVariable UUID accountId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return accountService.getLedger(accountId, pageable);
    }
}
