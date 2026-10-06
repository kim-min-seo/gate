package com.gate.reservation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
public class TossPaymentService {
    private final RestClient client = RestClient.builder().baseUrl("https://api.tosspayments.com/v1").build();
    private final String secretKey;

    public TossPaymentService(@Value("${TOSS_SECRET_KEY:}") String secretKey) { this.secretKey = secretKey; }

    public Map<?, ?> confirm(String paymentKey, String orderId, int amount) {
        if (secretKey == null || secretKey.isBlank()) throw new IllegalStateException("TOSS_SECRET_KEY가 설정되지 않았습니다.");
        String auth = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        return client.post().uri("/payments/confirm")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + auth)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount))
                .retrieve().body(Map.class);
    }

    public Map<?, ?> cancel(String paymentKey, String reason) {
        if (secretKey == null || secretKey.isBlank()) throw new IllegalStateException("TOSS_SECRET_KEY가 설정되지 않았습니다.");
        String auth = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        return client.post().uri("/payments/" + paymentKey + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + auth)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(Map.of("cancelReason", reason)).retrieve().body(Map.class);
    }
}
