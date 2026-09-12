package com.sahithireddy.paymentledger.repository;

import com.sahithireddy.paymentledger.domain.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Page<Payment> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
