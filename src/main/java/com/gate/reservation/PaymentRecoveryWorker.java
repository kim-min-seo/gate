package com.gate.reservation;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.LocalDateTime;

@Component
public class PaymentRecoveryWorker {
    private final PaymentRecoveryRepository jobs;
    private final PaymentRecoveryService service;
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(PaymentRecoveryWorker.class);
    public PaymentRecoveryWorker(PaymentRecoveryRepository jobs,PaymentRecoveryService service){this.jobs=jobs;this.service=service;}
    @Scheduled(fixedDelayString="${gate.payment.recovery-delay-ms:30000}")
    public void recover(){
        for(var job:jobs.findTop20ByStatusAndNextAttemptAtBeforeOrderByNextAttemptAtAsc("PENDING",LocalDateTime.now())){
            try{service.reconcile(job.getOrderId());}
            catch(RuntimeException failure){log.warn("Payment reconciliation transaction failed; record retained for retry");}
        }
    }
}
