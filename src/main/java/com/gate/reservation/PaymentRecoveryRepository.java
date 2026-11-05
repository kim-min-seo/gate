package com.gate.reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
public interface PaymentRecoveryRepository extends JpaRepository<PaymentRecovery,String> {
    List<PaymentRecovery> findTop20ByStatusAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(String status, LocalDateTime now);
    List<PaymentRecovery> findByUserIdAndStatusIn(Long userId, List<String> statuses);
}
