package com.gate.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    java.util.List<Reservation> findByUserIdOrderByCreatedAtDesc(Long userId);
    long countBySlotId(Long slotId);
    boolean existsBySlotIdAndUserIdAndStatus(Long slotId, Long userId, ReservationStatus status);
    java.util.Optional<Reservation> findByIdAndUserId(Long id, Long userId);
}
