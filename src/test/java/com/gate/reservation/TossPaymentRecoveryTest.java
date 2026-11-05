package com.gate.reservation;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;
import java.net.SocketTimeoutException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TossPaymentRecoveryTest {
    @Test void recoversApprovedPaymentAfterLostConfirmationResponse() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var gateway = new TossPaymentService("test_secret", builder);
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andExpect(header("Idempotency-Key", "gate-confirm-order-a"))
                .andRespond(withException(new SocketTimeoutException("simulated timeout")));
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/key-a"))
                .andRespond(withSuccess("{\"paymentKey\":\"key-a\",\"orderId\":\"order-a\",\"totalAmount\":10000,\"status\":\"DONE\"}", MediaType.APPLICATION_JSON));
        assertEquals("DONE", gateway.confirm("key-a", "order-a", 10000).get("status"));
        server.verify();
    }
    @Test void rejectsMismatchedGatewayAmount() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var gateway = new TossPaymentService("test_secret", builder);
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andRespond(withSuccess("{\"paymentKey\":\"key-a\",\"orderId\":\"order-a\",\"totalAmount\":1,\"status\":\"DONE\"}", MediaType.APPLICATION_JSON));
        assertThrows(IllegalStateException.class, () -> gateway.confirm("key-a", "order-a", 10000));
        server.verify();
    }
    @Test void rejectsLiveSecretWithoutNetworkRequest() {
        var gateway = new TossPaymentService("live_secret", RestClient.builder());
        assertThrows(IllegalStateException.class, () -> gateway.confirm("key-a", "order-a", 10000));
    }
}
