package com.gate.reservation;

import com.gate.slot.*;
import com.gate.seat.SeatRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderCancellationTest {
    @Test void cancelsOnlyReservationsLinkedToTheOrder() {
        var repo = mock(ReservationRepository.class);
        var slots = mock(SlotRepository.class);
        var payments = mock(PaymentRepository.class);
        var toss = mock(TossPaymentService.class);
        var service = new ReservationService(repo, slots, mock(SeatRepository.class), mock(StringRedisTemplate.class), payments, toss, mock(PaymentRecoveryRepository.class));
        var slot = new Slot("future", 100, LocalDateTime.now().plusDays(1));
        var first = new Reservation(1L, slot); first.setOrderId("order-a"); first.confirm();
        var second = new Reservation(1L, slot); second.setOrderId("order-a"); second.confirm();
        var other = new Reservation(1L, slot); other.setOrderId("order-b"); other.confirm();
        var payment = new Payment("test-key", "order-a", 20000, 1L, null);
        when(repo.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(first));
        when(slots.findByIdForUpdate(null)).thenReturn(Optional.of(slot));
        when(repo.findByOrderIdAndUserId("order-a", 1L)).thenReturn(List.of(first, second));
        when(payments.findByOrderId("order-a")).thenReturn(Optional.of(payment));
        service.cancel(1L, 1L);
        assertEquals(ReservationStatus.CANCELLED, first.getStatus());
        assertEquals(ReservationStatus.CANCELLED, second.getStatus());
        assertEquals(ReservationStatus.CONFIRMED, other.getStatus());
        assertEquals(PaymentStatus.CANCELLED, payment.getStatus());
        verify(toss).cancel(eq("test-key"), anyString());
    }
}
