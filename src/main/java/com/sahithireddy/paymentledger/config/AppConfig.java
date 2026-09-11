package com.sahithireddy.paymentledger.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({PaymentProperties.class, OutboxProperties.class})
public class AppConfig {
}
