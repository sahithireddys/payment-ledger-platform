package com.sahithireddy.paymentledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PaymentLedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentLedgerApplication.class, args);
    }
}
