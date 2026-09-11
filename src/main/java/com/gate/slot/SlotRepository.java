package com.gate.slot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface SlotRepository extends JpaRepository<Slot, Long> {
    boolean existsByStartsAt(java.time.LocalDateTime startsAt);
    java.util.List<Slot> findAllByOrderByStartsAtAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Slot s where s.id = :id")
    java.util.Optional<Slot> findByIdForUpdate(Long id);
}
