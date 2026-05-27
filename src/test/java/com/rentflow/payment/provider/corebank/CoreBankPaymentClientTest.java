package com.rentflow.payment.provider.corebank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.common.exception.PaymentProviderUnavailableException;
import com.rentflow.payment.config.CoreBankPaymentProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoreBankPaymentClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void authorizeHoldSerializesRequestAndParsesResponse() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/payments/authorize-hold", exchange -> {
            requestBody.set(readBody(exchange));
            respond(exchange, 200, """
                    {"paymentOrderId":"payment-order-1","holdId":"hold-1","status":"AUTHORIZED"}
                    """);
        });
        server.start();

        CoreBankPaymentClient client = client(server);
        CoreBankAuthorizeHoldResult result = client.authorizeHold(new CoreBankAuthorizeHoldRequest(
                "rentflow:authorize:booking:key",
                "payer-account-1",
                "escrow-account",
                1400000L,
                "VND",
                "MERCHANT_PAYMENT",
                "RentFlow booking payment",
                "rentflow:booking:booking-id",
                "rentflow",
                "correlation-1",
                "request-1",
                "session-1",
                "trace-1"));

        assertThat(requestBody.get()).contains("\"payerAccountId\":\"payer-account-1\"");
        assertThat(requestBody.get()).contains("\"payeeAccountId\":\"escrow-account\"");
        assertThat(requestBody.get()).contains("\"externalOrderRef\":\"rentflow:booking:booking-id\"");
        assertThat(requestBody.get()).contains("\"amountMinor\":1400000");
        assertThat(result.response().paymentOrderId()).isEqualTo("payment-order-1");
        assertThat(result.response().holdId()).isEqualTo("hold-1");
    }

    @Test
    void authorizeHoldTurns4xxIntoBusinessFailure() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/payments/authorize-hold", exchange ->
                respond(exchange, 400, """
                        {"code":"INSUFFICIENT_FUNDS","message":"Insufficient funds","status":"DECLINED"}
                        """));
        server.start();

        CoreBankPaymentClient client = client(server);

        assertThatThrownBy(() -> client.authorizeHold(new CoreBankAuthorizeHoldRequest(
                "key",
                "payer",
                "payee",
                100L,
                "VND",
                "MERCHANT_PAYMENT",
                "RentFlow booking payment",
                "rentflow:booking:1",
                "rentflow",
                "correlation",
                "request",
                "session",
                "trace")))
                .isInstanceOf(CoreBankAuthorizationFailedException.class)
                .hasMessageContaining("Insufficient funds");
    }

    @Test
    void authorizeHoldTurns5xxIntoProviderUnavailable() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/payments/authorize-hold", exchange ->
                respond(exchange, 503, """
                        {"message":"temporarily unavailable"}
                        """));
        server.start();

        CoreBankPaymentClient client = client(server);

        assertThatThrownBy(() -> client.authorizeHold(new CoreBankAuthorizeHoldRequest(
                "key",
                "payer",
                "payee",
                100L,
                "VND",
                "MERCHANT_PAYMENT",
                "RentFlow booking payment",
                "rentflow:booking:1",
                "rentflow",
                "correlation",
                "request",
                "session",
                "trace")))
                .isInstanceOf(PaymentProviderUnavailableException.class)
                .hasMessageContaining("authorize-hold");
    }

    @Test
    void voidHoldSerializesRequestAndParsesResponse() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/payments/void-hold", exchange -> {
            requestBody.set(readBody(exchange));
            respond(exchange, 200, """
                    {"holdId":"hold-1","status":"VOIDED"}
                    """);
        });
        server.start();

        CoreBankPaymentClient client = client(server);
        CoreBankVoidHoldResult result = client.voidHold(new CoreBankVoidHoldRequest(
                "rentflow:void:payment:key",
                "hold-1",
                "rentflow",
                "correlation-1",
                "request-1",
                "session-1",
                "trace-1"));

        assertThat(requestBody.get()).contains("\"holdId\":\"hold-1\"");
        assertThat(requestBody.get()).contains("\"idempotencyKey\":\"rentflow:void:payment:key\"");
        assertThat(result.response().status()).isEqualTo("VOIDED");
        assertThat(result.rawResponseJson()).contains("\"holdId\":\"hold-1\"");
    }

    private CoreBankPaymentClient client(HttpServer server) {
        CoreBankPaymentProperties properties = new CoreBankPaymentProperties();
        properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        return new CoreBankPaymentClient(RestClient.builder(), properties, new ObjectMapper());
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream inputStream = exchange.getRequestBody()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }
}
