package com.gate.reservation;

import com.gate.slot.Slot;
import com.gate.slot.SlotRepository;
import com.gate.seat.Seat;
import com.gate.seat.SeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Duration;

@Service
public class ReservationService {
    private final ReservationRepository reservations;
    private final SlotRepository slots;
    private final SeatRepository seats;
    private final StringRedisTemplate redis;

    public ReservationService(ReservationRepository reservations, SlotRepository slots, SeatRepository seats, StringRedisTemplate redis) {
        this.reservations = reservations; this.slots = slots; this.seats = seats; this.redis = redis;
    }

    @Transactional
    public Reservation reserveSeat(Long slotId, Long seatId, Long userId) {
        String rateKey = "gate:rate:user:" + userId;
        Long attempts = redis.opsForValue().increment(rateKey);
        if (attempts != null && attempts == 1) redis.expire(rateKey, Duration.ofSeconds(10));
        if (attempts != null && attempts > 10) throw new IllegalStateException("요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
        String key = "gate:lock:slot:" + slotId;
        Boolean acquired = redis.opsForValue().setIfAbsent(key, String.valueOf(userId), Duration.ofSeconds(10));
        if (!Boolean.TRUE.equals(acquired)) {
            redis.opsForList().rightPush("gate:queue:slot:" + slotId, String.valueOf(userId));
            throw new IllegalStateException("접속자가 많아 대기열에 등록되었습니다. 잠시 후 다시 시도해주세요.");
        }
        try {
            Seat seat = seats.findWithLockById(seatId).orElseThrow();
            if (!seat.getSlot().getId().equals(slotId) || seat.isReserved()) throw new IllegalStateException("이미 예약된 좌석입니다.");
            Reservation r = reserve(slotId, userId); seat.reserve(); r.assignSeat(seat); return r;
        } finally { redis.delete(key); }
    }

    public Long queuePosition(Long slotId, Long userId) {
        java.util.List<String> queue = redis.opsForList().range("gate:queue:slot:" + slotId, 0, -1);
        if (queue == null) return null;
        int index = queue.indexOf(String.valueOf(userId));
        return index < 0 ? null : (long) index + 1;
    }

    @Transactional
    public Reservation reserve(Long slotId, Long userId) {
        Slot slot = slots.findByIdForUpdate(slotId).orElseThrow();
        if (reservations.existsBySlotIdAndUserIdAndStatus(slotId, userId, ReservationStatus.HELD))
            throw new IllegalStateException("이미 신청한 시간대입니다.");
        if (slot.getRemaining() <= 0) throw new IllegalStateException("품절된 슬롯입니다.");
        slot.decreaseRemaining();
        Reservation reservation = new Reservation(userId, slot);
        reservations.save(reservation);
        return reservation;
    }

    @Transactional(readOnly = true)
    public java.util.List<Reservation> findMine(Long userId) {
        return reservations.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void cancel(Long reservationId, Long userId) {
        Reservation reservation = reservations.findByIdAndUserId(reservationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다."));
        if (reservation.getStatus() != ReservationStatus.HELD)
            throw new IllegalStateException("취소할 수 없는 예약 상태입니다.");
        reservation.cancel();
        if (reservation.getSeat() != null) reservation.getSeat().release();
        reservation.getSlot().increaseRemaining();
    }
}
