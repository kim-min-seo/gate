package com.gate.reservation;

import com.gate.slot.Slot;
import com.gate.slot.SlotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@SpringBootTest
class ConcurrencyExperimentTest {
    @Autowired SlotRepository slots;
    @Autowired ReservationRepository reservations;
    @Autowired ReservationService service;

    @Test
    void manyUsersReserveAtTheSameTime() throws Exception {
        Slot slot = slots.save(new Slot("동시성 실험", 100, LocalDateTime.now()));
        int users = 100;
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (long userId = 1; userId <= users; userId++) {
            long id = userId;
            results.add(pool.submit(() -> {
                start.await();
                try { service.reserve(slot.getId(), id); return "SUCCESS"; }
                catch (Exception e) { return "FAILED"; }
            }));
        }
        start.countDown();
        int success = 0;
        for (Future<String> result : results) if ("SUCCESS".equals(result.get())) success++;
        pool.shutdown();

        Slot after = slots.findById(slot.getId()).orElseThrow();
        long saved = reservations.countBySlotId(slot.getId());
        System.out.printf("CONCURRENCY RESULT: requests=%d, success=%d, reservations=%d, remaining=%d%n",
                users, success, saved, after.getRemaining());
        reservations.deleteAll(reservations.findAll().stream().filter(r -> r.getSlot().getId().equals(slot.getId())).toList());
        slots.delete(slot);
    }
}
