package com.gate.reservation;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationQueueWorker {
    private final StringRedisTemplate redis;
    private final com.gate.slot.SlotRepository slots;
    private final ReservationService reservations;
    private final ReservationRepository reservationRepository;
    public ReservationQueueWorker(StringRedisTemplate redis, com.gate.slot.SlotRepository slots, ReservationService reservations, ReservationRepository reservationRepository) { this.redis = redis; this.slots = slots; this.reservations = reservations; this.reservationRepository = reservationRepository; }

    @Scheduled(fixedDelay = 5000)
    public void cleanQueues() {
        for (Reservation r : reservationRepository.findByStatus(ReservationStatus.HELD)) if (r.isExpired()) { r.cancel(); if (r.getSeat() != null) r.getSeat().release(); r.getSlot().increaseRemaining(); }
        for (var slot : slots.findAll()) {
            long slotId = slot.getId();
            String key = "gate:queue:slot:" + slotId;
            var users = redis.opsForList().range(key, 0, -1);
            if (users == null || users.isEmpty()) continue;
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            for (String user : users) if (!seen.add(user)) redis.opsForList().remove(key, 1, user);
            String next = redis.opsForList().leftPop(key);
            if (next != null && slot.getRemaining() > 0) try { reservations.autoReserve(slotId, Long.parseLong(next)); } catch (RuntimeException ignored) { }
        }
    }
}
