package com.sahithireddy.paymentledger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payments")
public class PaymentProperties {

    private String submittedTopic = "payments.submitted";
    private String completedTopic = "payments.completed";
    private String failedTopic = "payments.failed";
    private String submittedDltTopic = "payments.submitted.DLT";
    private int partitions = 3;
    private int idempotencyTtlHours = 24;
    private int balanceCacheTtlSeconds = 30;

    public String getSubmittedTopic() { return submittedTopic; }
    public void setSubmittedTopic(String submittedTopic) { this.submittedTopic = submittedTopic; }

    public String getCompletedTopic() { return completedTopic; }
    public void setCompletedTopic(String completedTopic) { this.completedTopic = completedTopic; }

    public String getFailedTopic() { return failedTopic; }
    public void setFailedTopic(String failedTopic) { this.failedTopic = failedTopic; }

    public String getSubmittedDltTopic() { return submittedDltTopic; }
    public void setSubmittedDltTopic(String submittedDltTopic) { this.submittedDltTopic = submittedDltTopic; }

    public int getPartitions() { return partitions; }
    public void setPartitions(int partitions) { this.partitions = partitions; }

    public int getIdempotencyTtlHours() { return idempotencyTtlHours; }
    public void setIdempotencyTtlHours(int idempotencyTtlHours) { this.idempotencyTtlHours = idempotencyTtlHours; }

    public int getBalanceCacheTtlSeconds() { return balanceCacheTtlSeconds; }
    public void setBalanceCacheTtlSeconds(int balanceCacheTtlSeconds) { this.balanceCacheTtlSeconds = balanceCacheTtlSeconds; }
}
