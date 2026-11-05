package com.gate.reservation;

import com.gate.slot.*;
import com.gate.seat.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ReservationRaceRegressionTest {
    @Autowired ReservationService service;
    @Autowired ReservationRepository reservations;
    @Autowired SlotRepository slots;
    @Autowired SeatRepository seats;
    private Slot create(){var slot=slots.save(new Slot("race-test",100,LocalDateTime.now().plusDays(2)));service.ensureSeatInventory(slot.getId());return slot;}
    private void clean(Slot slot){reservations.deleteAll(reservations.findAll().stream().filter(r->r.getSlot().getId().equals(slot.getId())).toList());seats.deleteAll(seats.findAllBySlotOrderByLabelAsc(slot));slots.deleteById(slot.getId());}
    private int race(List<Runnable> actions) throws Exception {
        try(var pool=Executors.newFixedThreadPool(actions.size())){
            var start=new CountDownLatch(1);var futures=new ArrayList<Future<Boolean>>();
            for(var action:actions)futures.add(pool.submit(()->{start.await();try{action.run();return true;}catch(IllegalStateException rejected){return false;}}));
            start.countDown();int success=0;for(var future:futures)if(future.get(30,TimeUnit.SECONDS))success++;return success;
        }
    }
    @Test void twoAccountsCannotHoldSameSeat() throws Exception {
        var slot=create();var id=seats.findAllBySlotOrderByLabelAsc(slot).getFirst().getId();
        try{
            int successes=race(List.of(()->service.beginCheckout(slot.getId(),List.of(id),900001L,"race-a-"+slot.getId(),0),()->service.beginCheckout(slot.getId(),List.of(id),900002L,"race-b-"+slot.getId(),0)));
            assertEquals(1,successes);assertEquals(99,slots.findById(slot.getId()).orElseThrow().getRemaining());
        }finally{clean(slot);}
    }
    @Test void sameAccountMultipleTabsNeverAccumulateMoreThanFourHolds() throws Exception {
        var slot=create();
        try{
            var actions=new ArrayList<Runnable>();for(int i=0;i<8;i++){String order="tab-"+i+"-"+slot.getId();actions.add(()->service.beginCheckout(slot.getId(),null,900003L,order,4));}
            assertEquals(8,race(actions));
            assertEquals(4,reservations.findByUserIdAndSlotIdAndStatusIn(900003L,slot.getId(),List.of(ReservationStatus.HELD)).size());
            assertEquals(96,slots.findById(slot.getId()).orElseThrow().getRemaining());
        }finally{clean(slot);}
    }
    @Test void simultaneousCancellationDoesNotReturnSeatsTwice() throws Exception {
        var slot=create();
        try{
            service.beginCheckout(slot.getId(),null,900004L,"cancel-"+slot.getId(),4);
            Long id=reservations.findByOrderIdAndUserId("cancel-"+slot.getId(),900004L).getFirst().getId();
            assertEquals(1,race(List.of(()->service.cancel(id,900004L),()->service.cancel(id,900004L))));
            assertEquals(100,slots.findById(slot.getId()).orElseThrow().getRemaining());
            assertTrue(seats.findAllBySlotOrderByLabelAsc(slot).stream().noneMatch(Seat::isReserved));
        }finally{clean(slot);}
    }
}
