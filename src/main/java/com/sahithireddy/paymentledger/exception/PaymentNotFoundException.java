package com.sahithireddy.paymentledger.exception;

import java.util.UUID;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(UUID paymentId) {
        super("Payment not found: " + paymentId);
    }
}
