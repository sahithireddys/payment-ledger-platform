package com.sahithireddy.paymentledger.kafka.event;

public final class EventTypes {
    public static final String PAYMENT_SUBMITTED = "PAYMENT_SUBMITTED";
    public static final String PAYMENT_COMPLETED = "PAYMENT_COMPLETED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";

    private EventTypes() {}
}
