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
    private final PaymentRepository payments;
    private final TossPaymentService toss;

    public ReservationService(ReservationRepository reservations, SlotRepository slots, SeatRepository seats, StringRedisTemplate redis, PaymentRepository payments, TossPaymentService toss) {
        this.reservations = reservations; this.slots = slots; this.seats = seats; this.redis = redis; this.payments = payments; this.toss = toss;
    }
    public Slot getSlot(Long id) { return slots.findById(id).orElseThrow(); }
    public Boolean claimIdempotency(Long userId, String key) { return redis.opsForValue().setIfAbsent("gate:idempotency:" + userId + ":" + key, "done", Duration.ofMinutes(10)); }

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

    @Transactional
    public Reservation autoReserve(Long slotId, Long userId) {
        var available = seats.findAllBySlotOrderByLabelAsc(slots.findById(slotId).orElseThrow())
                .stream().filter(s -> !s.isReserved()).collect(java.util.stream.Collectors.toList());
        java.util.Collections.shuffle(available);
        for (Seat seat : available) {
            try { return reserveSeat(slotId, seat.getId(), userId); }
            catch (IllegalStateException ignored) { }
        }
        throw new IllegalStateException("남은 좌석이 없습니다.");
    }

    @Transactional
    public java.util.List<Reservation> reserveSeats(Long slotId, java.util.List<Long> seatIds, Long userId) {
        if (seatIds == null || seatIds.isEmpty() || seatIds.size() > 4) throw new IllegalStateException("좌석은 1~4개까지 선택할 수 있습니다.");
        java.util.List<Reservation> result = new java.util.ArrayList<>();
        for (Long seatId : seatIds) result.add(reserveSeat(slotId, seatId, userId));
        result.forEach(Reservation::confirm); return result;
    }

    @Transactional
    public void recordPaidOrder(Long slotId, java.util.List<Long> seatIds, Long userId,
                                String paymentKey, String orderId, int amount) {
        slots.findByIdForUpdate(slotId).orElseThrow();
        var booked = reservations.findByOrderIdAndUserId(orderId, userId);
        if (booked.size() != seatIds.size() || booked.stream().anyMatch(r -> r.getStatus() != ReservationStatus.HELD || r.isExpired()))
            throw new IllegalStateException("좌석 점유가 만료되었습니다.");
        booked.forEach(Reservation::confirm);
        payments.save(new Payment(paymentKey, orderId, amount, userId, slotId));
    }

    @Transactional
    public void holdOrder(Long slotId, java.util.List<Long> seatIds, Long userId, String orderId) {
        if (seatIds.isEmpty() || seatIds.size() > 4 || new java.util.HashSet<>(seatIds).size() != seatIds.size())
            throw new IllegalStateException("서로 다른 좌석을 1~4개 선택하세요.");
        var slot = slots.findByIdForUpdate(slotId).orElseThrow();
        if (!slot.getStartsAt().isAfter(java.time.LocalDateTime.now())) throw new IllegalStateException("지난 시간대입니다.");
        for (Long seatId : seatIds.stream().sorted().toList()) {
            var seat = seats.findWithLockById(seatId).orElseThrow();
            if (!seat.getSlot().getId().equals(slotId) || seat.isReserved()) throw new IllegalStateException("이미 점유된 좌석입니다.");
            var item = reserve(slotId, userId);
            seat.reserve(); item.assignSeat(seat); item.setOrderId(orderId);
        }
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
        long mine = reservations.countBySlotIdAndUserIdAndStatusIn(slotId, userId, java.util.List.of(ReservationStatus.HELD, ReservationStatus.CONFIRMED));
        if (mine >= 4) throw new IllegalStateException("한 시간대에 최대 4매까지 예약할 수 있습니다.");
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
        if (reservation.getSlot().getStartsAt().isBefore(java.time.LocalDateTime.now())) throw new IllegalStateException("입장 시간이 지난 예약은 취소할 수 없습니다.");
        if (reservation.getStatus() != ReservationStatus.HELD && reservation.getStatus() != ReservationStatus.CONFIRMED)
            throw new IllegalStateException("취소할 수 없는 예약 상태입니다.");
        slots.findByIdForUpdate(reservation.getSlot().getId()).orElseThrow();
        var active = reservation.getOrderId() == null
                ? java.util.List.of(reservation)
                : reservations.findByOrderIdAndUserId(reservation.getOrderId(), userId);
        if (reservation.getOrderId() != null) {
            var payment = payments.findByOrderId(reservation.getOrderId()).orElseThrow();
            if (payment.getStatus() != PaymentStatus.APPROVED) throw new IllegalStateException("Order already cancelled");
            toss.cancel(payment.getPaymentKey(), "Gate order cancellation");
            payment.cancel();
        }
        for (Reservation item : active) {
            item.cancel();
            if (item.getSeat() != null) item.getSeat().release();
            item.getSlot().increaseRemaining();
        }
    }
}
