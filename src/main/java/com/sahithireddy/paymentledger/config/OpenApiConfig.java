package com.sahithireddy.paymentledger.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentLedgerOpenApi() {
        return new OpenAPI().info(new Info()
            .title("Payment Ledger Platform API")
            .description("Distributed payment processing & double-entry ledger service. "
                + "Submits are validated and durably queued via a transactional outbox, "
                + "then processed asynchronously through Kafka with idempotent, "
                + "order-preserving consumers.")
            .version("v1"));
    }
}
