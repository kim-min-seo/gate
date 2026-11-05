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
class CheckoutReplacementTest {
    @Autowired ReservationService service;
    @Autowired ReservationRepository reservations;
    @Autowired SlotRepository slots;
    @Autowired SeatRepository seats;
    @Autowired PaymentRecoveryService recovery;
    @Autowired PaymentRecoveryRepository jobs;
    private Slot create(){var slot=slots.save(new Slot("replacement-test",100,LocalDateTime.now().plusDays(2)));service.ensureSeatInventory(slot.getId());return slot;}
    private List<Reservation> active(Slot slot,Long user){return reservations.findByUserIdAndSlotIdAndStatusIn(user,slot.getId(),List.of(ReservationStatus.HELD,ReservationStatus.CONFIRMED));}
    private void clean(Slot slot){
        var all=reservations.findAll().stream().filter(r->r.getSlot().getId().equals(slot.getId())).toList();
        for(var r:all)if(r.getOrderId()!=null && jobs.existsById(r.getOrderId()))jobs.deleteById(r.getOrderId());
        reservations.deleteAll(all);seats.deleteAll(seats.findAllBySlotOrderByLabelAsc(slot));slots.deleteById(slot.getId());
    }
    @Test void replacesFourAutoSeatsWithNewAutoAndManualSelection(){
        var slot=create();Long user=800001L;
        try{
            var first=service.beginCheckout(slot.getId(),null,user,"first-"+slot.getId(),4);
            var second=service.beginCheckout(slot.getId(),null,user,"second-"+slot.getId(),4);
            assertEquals(4,active(slot,user).size());
            assertTrue(reservations.findByOrderIdAndUserId("first-"+slot.getId(),user).stream().allMatch(r->r.getStatus()==ReservationStatus.CANCELLED));
            var selected=seats.findAllBySlotOrderByLabelAsc(slot).stream().filter(s->!second.contains(s.getId())).limit(4).map(Seat::getId).toList();
            service.beginCheckout(slot.getId(),selected,user,"third-"+slot.getId(),0);
            assertEquals(new HashSet<>(selected),new HashSet<>(active(slot,user).stream().map(r->r.getSeat().getId()).toList()));
            assertEquals(96,slots.findById(slot.getId()).orElseThrow().getRemaining());
        }finally{clean(slot);}
    }
    @Test void failedReplacementRollsBackAndKeepsOriginalHold(){
        var slot=create();Long user=800002L;
        try{
            var original=service.beginCheckout(slot.getId(),null,user,"original-"+slot.getId(),4);
            assertThrows(IllegalStateException.class,()->service.beginCheckout(slot.getId(),List.of(original.getFirst(),original.getFirst()),user,"bad-"+slot.getId(),0));
            assertEquals(4,active(slot,user).size());
            assertTrue(active(slot,user).stream().allMatch(r->r.getOrderId().equals("original-"+slot.getId())));
            assertEquals(96,slots.findById(slot.getId()).orElseThrow().getRemaining());
        }finally{clean(slot);}
    }
    @Test void neverReleasesOrderWhoseApprovalIsBeingReconciled(){
        var slot=create();Long user=800003L;
        try{
            String order="pending-"+slot.getId();var ids=service.beginCheckout(slot.getId(),null,user,order,4);
            recovery.prepare(order,"fake-"+order,user,slot.getId(),40000,ids);
            assertThrows(IllegalStateException.class,()->service.beginCheckout(slot.getId(),null,user,"next-"+slot.getId(),4));
            assertEquals(4,active(slot,user).size());assertEquals(96,slots.findById(slot.getId()).orElseThrow().getRemaining());
        }finally{clean(slot);}
    }
    @Test void replacementDoesNotReleaseAnotherUsersSeats(){
        var slot=create();
        try{
            service.beginCheckout(slot.getId(),null,800004L,"other-"+slot.getId(),4);
            service.beginCheckout(slot.getId(),null,800005L,"mine-a-"+slot.getId(),4);
            service.beginCheckout(slot.getId(),null,800005L,"mine-b-"+slot.getId(),4);
            assertEquals(4,active(slot,800004L).size());assertEquals(4,active(slot,800005L).size());
            assertEquals(92,slots.findById(slot.getId()).orElseThrow().getRemaining());
        }finally{clean(slot);}
    }
    @Test void confirmedReservationCannotBeReplacedAsAnUnpaidHold(){
        var slot=create();Long user=800006L;
        try{
            service.beginCheckout(slot.getId(),null,user,"confirmed-"+slot.getId(),4);
            var booked=active(slot,user);booked.forEach(Reservation::confirm);reservations.saveAll(booked);
            assertThrows(IllegalStateException.class,()->service.beginCheckout(slot.getId(),null,user,"new-"+slot.getId(),4));
            assertEquals(4,active(slot,user).size());
            assertTrue(active(slot,user).stream().allMatch(r->r.getStatus()==ReservationStatus.CONFIRMED));
            assertEquals(96,slots.findById(slot.getId()).orElseThrow().getRemaining());
        }finally{clean(slot);}
    }
}
