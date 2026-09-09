package com.gate.reservation;

import com.gate.slot.Slot;
import com.gate.seat.Seat;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Reservation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Slot slot;
    @ManyToOne(fetch = FetchType.LAZY)
    private Seat seat;
    @Enumerated(EnumType.STRING)
    private ReservationStatus status;
    private LocalDateTime createdAt;

    protected Reservation() {}

    public Reservation(Long userId, Slot slot) {
        this.userId = userId;
        this.slot = slot;
        this.status = ReservationStatus.HELD;
        this.createdAt = LocalDateTime.now();
    }
    public void assignSeat(Seat seat) { this.seat = seat; }
    public Seat getSeat() { return seat; }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Slot getSlot() { return slot; }
    public ReservationStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void cancel() { this.status = ReservationStatus.CANCELLED; }
}
