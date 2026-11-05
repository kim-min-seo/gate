package com.gate.reservation;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentReturnFlowTest {
    @Test void revisitingCompletedPaymentDoesNotRequireTemporaryRedisOrder(){
        var redis=mock(StringRedisTemplate.class);var gateway=mock(TossPaymentService.class);
        var service=mock(ReservationService.class);var repo=mock(PaymentRepository.class);var recovery=mock(PaymentRecoveryService.class);
        when(repo.findByOrderId("order-a")).thenReturn(Optional.of(new Payment("key-a","order-a",10000,1L,1L)));
        var session=new MockHttpSession();session.setAttribute("userId",1L);
        var controller=new PaymentController(redis,gateway,service,repo,recovery);
        assertEquals("redirect:/my-reservations",controller.success("key-a","order-a",10000,session,new RedirectAttributesModelMap()));
        verifyNoInteractions(redis,gateway,service,recovery);
    }
    @Test void expiredLoginRedirectsToLoginBeforeReadingPaymentData(){
        var redis=mock(StringRedisTemplate.class);var gateway=mock(TossPaymentService.class);
        var service=mock(ReservationService.class);var repo=mock(PaymentRepository.class);var recovery=mock(PaymentRecoveryService.class);
        var controller=new PaymentController(redis,gateway,service,repo,recovery);
        assertEquals("redirect:/login",controller.success("key-a","order-a",10000,new MockHttpSession(),new RedirectAttributesModelMap()));
        verifyNoInteractions(redis,gateway,service,repo,recovery);
    }
    @Test void anotherUsersCompletedOrderIsNotAccepted(){
        var redis=mock(StringRedisTemplate.class);var gateway=mock(TossPaymentService.class);
        var service=mock(ReservationService.class);var repo=mock(PaymentRepository.class);var recovery=mock(PaymentRecoveryService.class);
        when(repo.findByOrderId("order-a")).thenReturn(Optional.of(new Payment("key-a","order-a",10000,2L,1L)));
        var session=new MockHttpSession();session.setAttribute("userId",1L);
        var controller=new PaymentController(redis,gateway,service,repo,recovery);
        assertEquals("redirect:/slots",controller.success("key-a","order-a",10000,session,new RedirectAttributesModelMap()));
        verifyNoInteractions(redis,gateway,service,recovery);
    }
}
