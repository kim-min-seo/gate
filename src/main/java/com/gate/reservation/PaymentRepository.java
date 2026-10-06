package com.gate.reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderId(String orderId);
    boolean existsByPaymentKey(String paymentKey);
    java.util.List<Payment> findByUserIdOrderByApprovedAtDesc(Long userId);
    Optional<Payment> findFirstByUserIdAndSlotIdAndStatusOrderByApprovedAtDesc(Long userId, Long slotId, PaymentStatus status);
}
