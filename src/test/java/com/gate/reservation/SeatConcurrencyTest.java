package com.gate.reservation;

import com.gate.seat.*;
import com.gate.slot.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class SeatConcurrencyTest {
    @Autowired SlotRepository slots; @Autowired SeatRepository seats; @Autowired ReservationService service; @Autowired ReservationRepository reservations;

    @Test
    void onlyOneRequestCanReserveTheSameSeat() throws Exception {
        Slot slot = slots.save(new Slot("좌석 동시성 실험", 100, LocalDateTime.now()));
        Seat seat = seats.save(new Seat("TEST-A", slot));
        ExecutorService pool = Executors.newFixedThreadPool(10); CountDownLatch start = new CountDownLatch(1);
        var futures = new java.util.ArrayList<Future<Boolean>>();
        for (long user = 1; user <= 10; user++) { long id = user; futures.add(pool.submit(() -> { start.await(); try { service.reserveSeat(slot.getId(), seat.getId(), id); return true; } catch (Exception e) { return false; } })); }
        start.countDown(); int success = 0; for (var f : futures) if (f.get()) success++; pool.shutdown();
        assertEquals(1, success); reservations.deleteAll(reservations.findAll().stream().filter(r -> r.getSlot().getId().equals(slot.getId())).toList()); seats.deleteAll(seats.findAllBySlotOrderByLabelAsc(slot)); slots.delete(slot);
    }
}
