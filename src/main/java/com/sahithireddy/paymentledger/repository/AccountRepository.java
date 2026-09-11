package com.sahithireddy.paymentledger.repository;

import com.sahithireddy.paymentledger.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    /**
     * Row-level lock (SELECT ... FOR UPDATE) used by the ledger processor
     * before mutating a balance. Callers must always acquire locks on
     * multiple accounts in a consistent (id-ascending) order to avoid
     * deadlocking with a concurrent transfer running in the opposite
     * direction between the same two accounts.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);
}
