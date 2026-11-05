package com.gate.reservation;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckoutValidationTest {
    @Test void missingSeatSelectionRedirectsWithMessageWithoutCreatingOrder(){
        var service=mock(ReservationService.class);var redis=mock(StringRedisTemplate.class);
        var controller=new ReservationController(service,redis);
        var session=new MockHttpSession();session.setAttribute("userId",1L);
        var flash=new RedirectAttributesModelMap();
        assertEquals("redirect:/slots/1/seats",controller.payment(1L,null,session,new ExtendedModelMap(),flash));
        assertTrue(flash.getFlashAttributes().containsKey("message"));verifyNoInteractions(service,redis);
    }
    @Test void directPaymentUrlRedirectsToSlots(){
        var controller=new ReservationController(mock(ReservationService.class),mock(StringRedisTemplate.class));
        assertEquals("redirect:/slots",controller.paymentEntry(new RedirectAttributesModelMap()));
    }
}
