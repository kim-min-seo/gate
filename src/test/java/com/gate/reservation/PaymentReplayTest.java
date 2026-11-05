package com.gate.reservation;

import com.gate.slot.*;
import com.gate.seat.SeatRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentReplayTest {
    @Test void repeatedApprovedOrderDoesNotCallGatewayAgain() {
        var repo = mock(ReservationRepository.class);
        var slots = mock(SlotRepository.class);
        var payments = mock(PaymentRepository.class);
        var gateway = mock(TossPaymentService.class);
        var service = new ReservationService(repo, slots, mock(SeatRepository.class), mock(StringRedisTemplate.class), payments, gateway, mock(PaymentRecoveryRepository.class));
        when(slots.findByIdForUpdate(1L)).thenReturn(Optional.of(new Slot("future",100,LocalDateTime.now().plusDays(1))));
        when(payments.findByOrderId("order-a")).thenReturn(Optional.of(new Payment("key-a","order-a",10000,1L,1L)));
        service.recordPaidOrder(1L,List.of(1L),1L,"key-a","order-a",10000);
        service.recordPaidOrder(1L,List.of(1L),1L,"key-a","order-a",10000);
        verifyNoInteractions(gateway,repo);
        verify(payments, never()).save(any());
        assertThrows(IllegalStateException.class, () -> service.recordPaidOrder(1L,List.of(1L),2L,"key-a","order-a",10000));
    }
}
