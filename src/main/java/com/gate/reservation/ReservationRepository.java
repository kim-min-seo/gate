package com.gate.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    java.util.List<Reservation> findByUserIdOrderByCreatedAtDesc(Long userId);
    long countBySlotId(Long slotId);
    long countBySlotIdAndUserIdAndStatusIn(Long slotId, Long userId, java.util.Collection<ReservationStatus> statuses);
    java.util.List<Reservation> findByStatus(ReservationStatus status);
    boolean existsBySlotIdAndUserIdAndStatus(Long slotId, Long userId, ReservationStatus status);
    java.util.Optional<Reservation> findByIdAndUserId(Long id, Long userId);
}
