package com.gate.reservation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class CancellationRecoveryTest {
    @Test void gatewayCancellationCompletesLocalOrderAndDoesNotReleaseTwice(){
        var f=new PaymentReconciliationTest.Fixture();f.job.requestCancellation();f.held.confirm();
        var local=new Payment("key-a","order-a",10000,1L,1L);
        when(f.payments.findByOrderId("order-a")).thenReturn(Optional.of(local));
        doReturn(Map.of("status","CANCELED")).when(f.gateway).cancel(eq("key-a"),anyString());
        f.service.reconcile("order-a");f.service.reconcile("order-a");
        assertEquals(PaymentStatus.CANCELLED,local.getStatus());assertEquals(ReservationStatus.CANCELLED,f.held.getStatus());
        assertEquals(100,f.slot.getRemaining());assertEquals("RESOLVED",f.job.getStatus());
        verify(f.gateway,times(1)).cancel(eq("key-a"),anyString());
    }
    @Test void failedCancellationKeepsConfirmedReservationAndApprovedPayment(){
        var f=new PaymentReconciliationTest.Fixture();f.job.requestCancellation();f.held.confirm();
        var local=new Payment("key-a","order-a",10000,1L,1L);
        when(f.payments.findByOrderId("order-a")).thenReturn(Optional.of(local));
        when(f.gateway.cancel(eq("key-a"),anyString())).thenThrow(new IllegalStateException());
        f.service.reconcile("order-a");
        assertEquals(PaymentStatus.APPROVED,local.getStatus());assertEquals(ReservationStatus.CONFIRMED,f.held.getStatus());
        assertEquals(99,f.slot.getRemaining());assertEquals("PENDING",f.job.getStatus());
    }
    @Test void alreadyCancelledRemotePaymentOnlyUpdatesLocalState(){
        var f=new PaymentReconciliationTest.Fixture();f.job.requestCancellation();f.held.confirm();
        var local=new Payment("key-a","order-a",10000,1L,1L);
        when(f.payments.findByOrderId("order-a")).thenReturn(Optional.of(local));
        doReturn(Map.of("paymentKey","key-a","orderId","order-a","totalAmount",10000,"status","CANCELED")).when(f.gateway).find("key-a");
        f.service.reconcile("order-a");
        assertEquals(PaymentStatus.CANCELLED,local.getStatus());assertEquals(100,f.slot.getRemaining());
        verify(f.gateway,never()).cancel(anyString(),anyString());
    }
}
