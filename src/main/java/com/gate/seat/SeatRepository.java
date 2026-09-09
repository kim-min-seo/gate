package com.gate.seat;

import com.gate.slot.Slot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findAllBySlotOrderByLabelAsc(Slot slot);
    long countBySlot(Slot slot);
    boolean existsBySlotAndLabel(Slot slot, String label);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<Seat> findWithLockById(Long id);
}
