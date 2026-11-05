package com.gate.reservation;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class PaymentValidationTest {
    private void reject(String payload, Long userId, int amount) {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String,String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("gate:payment:order:order-test")).thenReturn(payload);
        var toss = mock(TossPaymentService.class);
        var reservations = mock(ReservationService.class);
        var controller = new PaymentController(redis, toss, reservations, mock(PaymentRepository.class));
        var session = new MockHttpSession(); session.setAttribute("userId", userId);
        var attributes = new RedirectAttributesModelMap();
        assertEquals("redirect:/slots", controller.success("fake-key", "order-test", amount, session, attributes));
        verifyNoInteractions(toss, reservations);
        assertTrue(attributes.getFlashAttributes().containsKey("message"));
    }
    @Test void rejectsChangedAmountBeforeGatewayCall() { reject("1|11,12|1", 1L, 1); }
    @Test void rejectsOtherUsersOrderBeforeGatewayCall() { reject("1|11|2", 1L, 10000); }
    @Test void rejectsExpiredOrderBeforeGatewayCall() { reject(null, 1L, 10000); }
}
