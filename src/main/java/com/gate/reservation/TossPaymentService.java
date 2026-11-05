package com.gate.reservation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
public class TossPaymentService {
    private final RestClient client;
    private final String secretKey;

    @Autowired
    public TossPaymentService(@Value("${TOSS_SECRET_KEY:}") String secretKey) {
        this(secretKey, configuredBuilder());
    }
    TossPaymentService(String secretKey, RestClient.Builder builder) {
        this.secretKey = secretKey;
        this.client = builder.baseUrl("https://api.tosspayments.com/v1").build();
    }
    private static RestClient.Builder configuredBuilder() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); factory.setReadTimeout(15000);
        return RestClient.builder().requestFactory(factory);
    }
    private String authorization() {
        if (secretKey == null || !secretKey.startsWith("test_")) throw new IllegalStateException("테스트 결제 키가 필요합니다.");
        return "Basic " + Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
    }
    public Map<?, ?> confirm(String paymentKey, String orderId, int amount) {
        Map<?, ?> result;
        try {
            result = client.post().uri("/payments/confirm")
                    .header(HttpHeaders.AUTHORIZATION, authorization())
                    .header("Idempotency-Key", "gate-confirm-" + orderId)
                    .body(Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount))
                    .retrieve().body(Map.class);
        } catch (ResourceAccessException | RestClientResponseException failure) {
            // A lost response is not proof that payment failed. Query before changing local state.
            try { result = find(paymentKey); }
            catch (RuntimeException lookupFailure) {
                throw new IllegalStateException("결제 상태 확인이 지연되고 있습니다. 다시 결제하지 말고 주문 상태를 확인해 주세요.", lookupFailure);
            }
        }
        if (result == null || !paymentKey.equals(result.get("paymentKey")) || !orderId.equals(result.get("orderId"))
                || !(result.get("totalAmount") instanceof Number total) || total.longValue() != amount
                || !"DONE".equals(result.get("status")))
            throw new IllegalStateException("결제 승인 완료를 확인하지 못했습니다. 주문 상태 확인이 필요합니다.");
        return result;
    }
    public Map<?, ?> find(String paymentKey) {
        return client.get().uri("/payments/{key}", paymentKey)
                .header(HttpHeaders.AUTHORIZATION, authorization()).retrieve().body(Map.class);
    }
    public Map<?, ?> cancel(String paymentKey, String reason) {
        return client.post().uri("/payments/{key}/cancel", paymentKey)
                .header(HttpHeaders.AUTHORIZATION, authorization())
                .header("Idempotency-Key", "gate-cancel-" + paymentKey)
                .body(Map.of("cancelReason", reason)).retrieve().body(Map.class);
    }
}
