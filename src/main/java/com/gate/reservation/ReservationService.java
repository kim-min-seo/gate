package com.gate.reservation;

import com.gate.slot.Slot;
import com.gate.slot.SlotRepository;
import com.gate.seat.Seat;
import com.gate.seat.SeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {
    private final ReservationRepository reservations;
    private final SlotRepository slots;
    private final SeatRepository seats;

    public ReservationService(ReservationRepository reservations, SlotRepository slots, SeatRepository seats) {
        this.reservations = reservations; this.slots = slots; this.seats = seats;
    }

    @Transactional
    public Reservation reserveSeat(Long slotId, Long seatId, Long userId) {
        Seat seat = seats.findWithLockById(seatId).orElseThrow();
        if (!seat.getSlot().getId().equals(slotId) || seat.isReserved()) throw new IllegalStateException("이미 예약된 좌석입니다.");
        Reservation r = reserve(slotId, userId); seat.reserve(); r.assignSeat(seat); return r;
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
