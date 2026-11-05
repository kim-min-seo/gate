package com.gate.reservation;

import com.gate.slot.*;
import com.gate.seat.Seat;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class PaymentReconciliationTest {
    static class Fixture {
        final PaymentRecoveryRepository jobs=mock(PaymentRecoveryRepository.class);
        final ReservationRepository reservations=mock(ReservationRepository.class);
        final PaymentRepository payments=mock(PaymentRepository.class);
        final SlotRepository slots=mock(SlotRepository.class);
        final TossPaymentService gateway=mock(TossPaymentService.class);
        final Slot slot=new Slot("future",100,LocalDateTime.now().plusDays(1));
        final Reservation held=new Reservation(1L,slot);
        final PaymentRecovery job=new PaymentRecovery("order-a","key-a",1L,1L,10000);
        final PaymentRecoveryService service=new PaymentRecoveryService(jobs,reservations,payments,slots,gateway);
        Fixture(){
            held.setOrderId("order-a");held.assignSeat(mock(Seat.class));slot.decreaseRemaining();
            when(jobs.findById("order-a")).thenReturn(Optional.of(job));
            when(slots.findByIdForUpdate(1L)).thenReturn(Optional.of(slot));
            when(reservations.findByOrderIdAndUserId("order-a",1L)).thenReturn(List.of(held));
            doReturn(Map.of("paymentKey","key-a","orderId","order-a","totalAmount",10000,"status","DONE")).when(gateway).find("key-a");
        }
        void expire() throws Exception {
            var field=Reservation.class.getDeclaredField("expiresAt");field.setAccessible(true);
            field.set(held,LocalDateTime.now().minusMinutes(1));
        }
    }
    @Test void approvedGatewayPaymentRestoresLocalReservationWithoutAnotherCharge(){
        var f=new Fixture();f.service.reconcile("order-a");
        assertEquals(ReservationStatus.CONFIRMED,f.held.getStatus());
        assertEquals("RESOLVED",f.job.getStatus());
        verify(f.payments).save(any(Payment.class));verify(f.gateway,never()).confirm(anyString(),anyString(),anyInt());
        verify(f.gateway,never()).cancel(anyString(),anyString());
    }
    @Test void expiredSeatHoldCausesFullCancellationAndRelease() throws Exception {
        var f=new Fixture();f.expire();
        doReturn(Map.of("status","CANCELED")).when(f.gateway).cancel(eq("key-a"),anyString());
        f.service.reconcile("order-a");
        assertEquals("RESOLVED",f.job.getStatus());assertEquals(ReservationStatus.CANCELLED,f.held.getStatus());
        assertEquals(100,f.slot.getRemaining());verify(f.payments).save(argThat(p -> p.getStatus()==PaymentStatus.CANCELLED));
    }
    @Test void failedRefundRemainsPendingAndDoesNotReleaseSeats() throws Exception {
        var f=new Fixture();f.expire();when(f.gateway.cancel(eq("key-a"),anyString())).thenThrow(new IllegalStateException());
        f.service.reconcile("order-a");
        assertEquals("PENDING",f.job.getStatus());assertEquals(1,f.job.getAttempts());
        assertEquals(ReservationStatus.HELD,f.held.getStatus());assertEquals(99,f.slot.getRemaining());
    }
    @Test void existingValidPaymentIsNeverCancelled(){
        var f=new Fixture();when(f.payments.findByOrderId("order-a")).thenReturn(Optional.of(new Payment("key-a","order-a",10000,1L,1L)));
        f.service.reconcile("order-a");assertEquals("RESOLVED",f.job.getStatus());verifyNoInteractions(f.gateway);
    }
    @Test void mismatchedGatewayDataRequiresManualReview(){
        var f=new Fixture();doReturn(Map.of("orderId","other")).when(f.gateway).find("key-a");
        f.service.reconcile("order-a");assertEquals("MANUAL_REVIEW",f.job.getStatus());verify(f.gateway,never()).cancel(anyString(),anyString());
    }
    @Test void persistentOutageStopsAutomaticRetriesAfterTenAttempts(){
        var f=new Fixture();when(f.gateway.find("key-a")).thenThrow(new IllegalStateException());
        for(int i=0;i<11;i++)f.service.reconcile("order-a");
        assertEquals("MANUAL_REVIEW",f.job.getStatus());assertEquals(10,f.job.getAttempts());
        verify(f.gateway,times(10)).find("key-a");
    }
}
