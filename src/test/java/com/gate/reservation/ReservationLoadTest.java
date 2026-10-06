package com.gate.reservation;

import com.gate.slot.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class ReservationLoadTest {
    @Autowired SlotRepository slots; @Autowired ReservationService service; @Autowired ReservationRepository reservations;

    @Test
    void concurrentRequestsNeverExceedCapacity() throws Exception {
        Slot slot = slots.save(new Slot("부하 실험", 100, LocalDateTime.now()));
        ExecutorService pool = Executors.newFixedThreadPool(40); CountDownLatch start = new CountDownLatch(1);
        var futures = new java.util.ArrayList<Future<Boolean>>();
        for (long user = 1; user <= 200; user++) { long id = user; futures.add(pool.submit(() -> { start.await(); try { service.reserve(slot.getId(), id); return true; } catch (Exception e) { return false; } })); }
        long begin = System.nanoTime(); start.countDown(); int success = 0; for (var f : futures) if (f.get()) success++; long elapsed = (System.nanoTime()-begin)/1_000_000; pool.shutdown();
        long saved = reservations.countBySlotId(slot.getId());
        System.out.printf("LOAD RESULT: requests=200 success=%d saved=%d elapsedMs=%d remaining=%d%n", success, saved, elapsed, slots.findById(slot.getId()).orElseThrow().getRemaining());
        assertEquals(100, success); reservations.deleteAll(reservations.findAll().stream().filter(r -> r.getSlot().getId().equals(slot.getId())).toList()); slots.delete(slot);
    }
}
