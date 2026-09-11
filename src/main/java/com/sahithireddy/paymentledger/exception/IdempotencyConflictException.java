package com.sahithireddy.paymentledger.exception;

/**
 * Thrown when an Idempotency-Key is reused with a request body that differs
 * from the original request that key was first used for.
 */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String idempotencyKey) {
        super("Idempotency-Key '" + idempotencyKey + "' was already used with a different request payload");
    }
}
