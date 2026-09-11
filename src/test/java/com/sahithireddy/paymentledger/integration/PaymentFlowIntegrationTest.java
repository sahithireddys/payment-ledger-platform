package com.sahithireddy.paymentledger.integration;

import com.sahithireddy.paymentledger.api.dto.AccountDtos.AccountResponse;
import com.sahithireddy.paymentledger.api.dto.AccountDtos.CreateAccountRequest;
import com.sahithireddy.paymentledger.api.dto.PaymentDtos.CreatePaymentRequest;
import com.sahithireddy.paymentledger.api.dto.PaymentDtos.PaymentResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end proof that the whole pipeline — REST API, transactional
 * outbox, Kafka, and the ledger-processing consumer — behaves correctly
 * against real Postgres and Kafka, not mocks. Requires Docker; run locally
 * with {@code mvn clean verify}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("payment_ledger")
        .withUsername("pll")
        .withPassword("pll");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @LocalServerPort
    private int port;

    private final TestRestTemplate rest = new TestRestTemplate();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private AccountResponse createAccount(BigDecimal initialBalance) {
        var request = new CreateAccountRequest("Test User", "USD", initialBalance);
        return rest.postForObject(url("/api/v1/accounts"), request, AccountResponse.class);
    }

    private PaymentResponse submitPayment(java.util.UUID from, java.util.UUID to, BigDecimal amount, String idempotencyKey) {
        var request = new CreatePaymentRequest(from, to, amount, "USD");
        HttpHeaders headers = new HttpHeaders();
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return rest.exchange(url("/api/v1/payments"), HttpMethod.POST,
            new HttpEntity<>(request, headers), PaymentResponse.class).getBody();
    }

    private AccountResponse getAccount(java.util.UUID id) {
        return rest.getForObject(url("/api/v1/accounts/" + id), AccountResponse.class);
    }

    @Test
    void fullPaymentFlow_completesAsynchronouslyAndPostsCorrectLedgerEntries() {
        AccountResponse from = createAccount(new BigDecimal("500.00"));
        AccountResponse to = createAccount(new BigDecimal("100.00"));

        PaymentResponse submitted = submitPayment(from.id(), to.id(), new BigDecimal("75.00"), null);
        assertThat(submitted.status()).isEqualTo("PENDING");

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            PaymentResponse fetched = rest.getForObject(url("/api/v1/payments/" + submitted.id()), PaymentResponse.class);
            assertThat(fetched.status()).isEqualTo("COMPLETED");
        });

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(getAccount(from.id()).balance()).isEqualByComparingTo("425.00");
            assertThat(getAccount(to.id()).balance()).isEqualByComparingTo("175.00");
        });
    }

    @Test
    void duplicateIdempotencyKey_doesNotDoubleProcessThePayment() {
        AccountResponse from = createAccount(new BigDecimal("500.00"));
        AccountResponse to = createAccount(new BigDecimal("0.00"));
        String idempotencyKey = "test-key-" + java.util.UUID.randomUUID();

        PaymentResponse first = submitPayment(from.id(), to.id(), new BigDecimal("50.00"), idempotencyKey);
        PaymentResponse second = submitPayment(from.id(), to.id(), new BigDecimal("50.00"), idempotencyKey);

        assertThat(second.id()).isEqualTo(first.id());

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
            assertThat(getAccount(to.id()).balance()).isEqualByComparingTo("50.00"));

        // Give any accidental second processing a chance to happen, then confirm it didn't.
        await().pollDelay(2, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> assertThat(getAccount(to.id()).balance()).isEqualByComparingTo("50.00"));
    }

    @Test
    void concurrentPaymentsFromTheSameAccount_neverLoseAnUpdate() {
        AccountResponse source = createAccount(new BigDecimal("10000.00"));
        int concurrentPayments = 25;
        BigDecimal amountEach = new BigDecimal("10.00");

        List<AccountResponse> destinations = IntStream.range(0, concurrentPayments)
            .mapToObj(i -> createAccount(BigDecimal.ZERO))
            .toList();

        List<CompletableFuture<PaymentResponse>> futures = IntStream.range(0, concurrentPayments)
            .mapToObj(i -> CompletableFuture.supplyAsync(() ->
                submitPayment(source.id(), destinations.get(i).id(), amountEach, null)))
            .toList();
        futures.forEach(CompletableFuture::join);

        BigDecimal expectedFinalBalance = new BigDecimal("10000.00")
            .subtract(amountEach.multiply(BigDecimal.valueOf(concurrentPayments)));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
            assertThat(getAccount(source.id()).balance()).isEqualByComparingTo(expectedFinalBalance));
    }
}
