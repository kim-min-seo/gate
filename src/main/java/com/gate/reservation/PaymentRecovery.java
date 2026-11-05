package com.gate.reservation;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Written before the gateway call so a process crash cannot silently lose a payment attempt. */
@Entity
public class PaymentRecovery {
    @Id private String orderId;
    private String paymentKey;
    private Long userId;
    private Long slotId;
    private int amount;
    private String status;
    private int attempts;
    private String lastError;
    private LocalDateTime nextAttemptAt;
    protected PaymentRecovery() {}
    public PaymentRecovery(String orderId, String paymentKey, Long userId, Long slotId, int amount) {
        this.orderId=orderId; this.paymentKey=paymentKey; this.userId=userId; this.slotId=slotId; this.amount=amount;
        this.status="PENDING"; this.nextAttemptAt=LocalDateTime.now().plusMinutes(1);
    }
    public String getOrderId(){return orderId;}
    public String getPaymentKey(){return paymentKey;}
    public Long getUserId(){return userId;}
    public Long getSlotId(){return slotId;}
    public int getAmount(){return amount;}
    public String getStatus(){return status;}
    public int getAttempts(){return attempts;}
    public String getLastError(){return lastError;}
    public void resolve(){status="RESOLVED";lastError=null;}
    public void manual(String reason){status="MANUAL_REVIEW";lastError=reason;}
    public void retry(String reason){
        attempts++; lastError=reason;
        nextAttemptAt=LocalDateTime.now().plusSeconds(Math.min(1800,60L * attempts));
        if(attempts>=10) status="MANUAL_REVIEW";
    }
}
