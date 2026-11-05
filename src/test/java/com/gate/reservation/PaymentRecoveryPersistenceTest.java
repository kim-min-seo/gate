package com.gate.reservation;

import com.gate.slot.*;
import com.gate.seat.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PaymentRecoveryPersistenceTest {
    @Autowired PaymentRecoveryService recovery;
    @Autowired PaymentRecoveryRepository jobs;
    @Autowired ReservationService service;
    @Autowired ReservationRepository reservations;
    @Autowired SlotRepository slots;
    @Autowired SeatRepository seats;
    @Test void recoveryRecordSurvivesReservationTransactionRollback(){
        String order="recovery-test-"+UUID.randomUUID();
        var slot=slots.save(new Slot("recovery test",100,LocalDateTime.now().plusDays(2)));
        var seat=seats.save(new Seat("test-A",slot));
        try {
            service.holdOrder(slot.getId(),List.of(seat.getId()),999999L,order);
            recovery.prepare(order,"test-key-"+order,999999L,slot.getId(),10000,List.of(seat.getId()));
            assertThrows(IllegalStateException.class,()-> service.recordPaidOrder(slot.getId(),List.of(),999999L,"test-key-"+order,order,10000));
            assertEquals("PENDING",jobs.findById(order).orElseThrow().getStatus());
            assertEquals(ReservationStatus.HELD,reservations.findByOrderIdAndUserId(order,999999L).getFirst().getStatus());
        } finally {
            jobs.deleteById(order);
            reservations.deleteAll(reservations.findByOrderIdAndUserId(order,999999L));
            seats.deleteById(seat.getId());slots.deleteById(slot.getId());
        }
    }
}
