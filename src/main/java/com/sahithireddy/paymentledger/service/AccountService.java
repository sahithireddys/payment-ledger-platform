package com.sahithireddy.paymentledger.service;

import com.sahithireddy.paymentledger.api.dto.AccountDtos.AccountResponse;
import com.sahithireddy.paymentledger.api.dto.AccountDtos.CreateAccountRequest;
import com.sahithireddy.paymentledger.api.dto.AccountDtos.LedgerEntryResponse;
import com.sahithireddy.paymentledger.config.PaymentProperties;
import com.sahithireddy.paymentledger.domain.Account;
import com.sahithireddy.paymentledger.domain.LedgerEntry;
import com.sahithireddy.paymentledger.exception.AccountNotFoundException;
import com.sahithireddy.paymentledger.repository.AccountRepository;
import com.sahithireddy.paymentledger.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private static final String BALANCE_CACHE_PREFIX = "balance:account:";

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final StringRedisTemplate redisTemplate;
    private final PaymentProperties paymentProperties;

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        Account account = Account.builder()
            .id(UUID.randomUUID())
            .ownerName(request.ownerName())
            .currency(request.currency())
            .balance(request.initialBalance())
            .build();
        accountRepository.save(account);
        return toResponse(account);
    }

    /**
     * Cache-aside read: serves the balance from Redis when present, falling
     * back to Postgres (the source of truth) on a miss and repopulating the
     * cache with a short TTL. Keeps the hot "check my balance" path cheap
     * without ever letting a stale cache be the system of record.
     */
    public AccountResponse getAccount(UUID accountId) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId));

        String cacheKey = BALANCE_CACHE_PREFIX + accountId;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        BigDecimal balance = cached != null ? new BigDecimal(cached) : account.getBalance();
        if (cached == null) {
            redisTemplate.opsForValue().set(cacheKey, account.getBalance().toPlainString(),
                Duration.ofSeconds(paymentProperties.getBalanceCacheTtlSeconds()));
        }

        return new AccountResponse(account.getId(), account.getOwnerName(), account.getCurrency(),
            balance, account.getCreatedAt());
    }

    public void invalidateBalanceCache(UUID accountId) {
        redisTemplate.delete(BALANCE_CACHE_PREFIX + accountId);
    }

    public Page<LedgerEntryResponse> getLedger(UUID accountId, Pageable pageable) {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        return ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable)
            .map(this::toLedgerResponse);
    }

    private AccountResponse toResponse(Account account) {
        return new AccountResponse(account.getId(), account.getOwnerName(), account.getCurrency(),
            account.getBalance(), account.getCreatedAt());
    }

    private LedgerEntryResponse toLedgerResponse(LedgerEntry entry) {
        return new LedgerEntryResponse(entry.getId(), entry.getAccountId(), entry.getPaymentId(),
            entry.getEntryType().name(), entry.getAmount(), entry.getBalanceAfter(), entry.getCreatedAt());
    }
}
