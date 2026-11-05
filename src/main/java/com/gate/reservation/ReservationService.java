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
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

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
        var existing = payments.findByOrderId(orderId).orElse(null);
        if (existing != null) {
            if (!existing.getUserId().equals(userId) || existing.getAmount() != amount || !existing.getPaymentKey().equals(paymentKey))
                throw new IllegalStateException("결제 주문 정보가 일치하지 않습니다.");
            if (existing.getStatus() != PaymentStatus.APPROVED) throw new IllegalStateException("이미 취소된 주문입니다.");
            return;
        }
        var booked = reservations.findByOrderIdAndUserId(orderId, userId);
        if (booked.size() != seatIds.size() || booked.stream().anyMatch(r -> r.getStatus() != ReservationStatus.HELD || r.isExpired()))
            throw new IllegalStateException("좌석 점유가 만료되었습니다.");
        toss.confirm(paymentKey, orderId, amount);
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

    @Transactional
    public void ensureSeatInventory(Long slotId) {
        var slot=slots.findByIdForUpdate(slotId).orElseThrow();
        var existing=seats.findAllBySlotOrderByLabelAsc(slot);
        if(existing.size()>=100) return;
        var labels=new java.util.HashSet<>(existing.stream().map(Seat::getLabel).toList());
        var missing=new java.util.ArrayList<Seat>();
        for(int row=1;row<=10;row++) for(char column='A';column<='J';column++){
            var label=row+"-"+column;
            if(!labels.contains(label)) missing.add(new Seat(label,slot));
        }
        seats.saveAll(missing);
    }

    @Transactional
    public java.util.List<Long> selectAvailableSeats(Long slotId, int count) {
        if (count < 1 || count > 4) throw new IllegalStateException("1~4매를 선택하세요.");
        ensureSeatInventory(slotId);
        var available = new java.util.ArrayList<>(seats.findAllBySlotOrderByLabelAsc(getSlot(slotId))
                .stream().filter(seat -> !seat.isReserved()).toList());
        java.util.Collections.shuffle(available);
        if (available.size() < count) throw new IllegalStateException("남은 좌석이 부족합니다.");
        return available.subList(0, count).stream().map(Seat::getId).toList();
    }

    @Transactional
    public void expireReservation(Long id, Long slotId) {
        slots.findByIdForUpdate(slotId).orElseThrow();
        var item = reservations.findById(id).orElseThrow();
        entityManager.refresh(item);
        if (!item.isExpired()) return;
        item.cancel();
        if (item.getSeat() != null) item.getSeat().release();
        item.getSlot().increaseRemaining();
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
        slots.findByIdForUpdate(reservation.getSlot().getId()).orElseThrow();
        if (entityManager != null) entityManager.refresh(reservation);
        if (reservation.getSlot().getStartsAt().isBefore(java.time.LocalDateTime.now())) throw new IllegalStateException("입장 시간이 지난 예약은 취소할 수 없습니다.");
        if (reservation.getStatus() != ReservationStatus.HELD && reservation.getStatus() != ReservationStatus.CONFIRMED)
            throw new IllegalStateException("취소할 수 없는 예약 상태입니다.");
        slots.findByIdForUpdate(reservation.getSlot().getId()).orElseThrow();
        var active = reservation.getOrderId() == null
                ? java.util.List.of(reservation)
                : reservations.findByOrderIdAndUserId(reservation.getOrderId(), userId);
        if (reservation.getOrderId() != null) {
            var payment = payments.findByOrderId(reservation.getOrderId()).orElse(null);
            if (payment != null) {
                if (entityManager != null) entityManager.refresh(payment);
                if (payment.getStatus() != PaymentStatus.APPROVED) throw new IllegalStateException("이미 취소된 주문입니다.");
                toss.cancel(payment.getPaymentKey(), "Gate order cancellation");
                payment.cancel();
            } else if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
                throw new IllegalStateException("결제 연결 확인이 필요합니다. 관리자에게 문의하세요.");
            }
        }
        for (Reservation item : active) {
            if (entityManager != null) entityManager.refresh(item);
            if (item.getStatus() != ReservationStatus.HELD && item.getStatus() != ReservationStatus.CONFIRMED) continue;
            item.cancel();
            if (item.getSeat() != null) item.getSeat().release();
            item.getSlot().increaseRemaining();
        }
    }
}
