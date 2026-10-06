package com.gate.reservation;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String paymentKey;
    @Column(nullable = false, unique = true)
    private String orderId;
    @Column(nullable = false)
    private int amount;
    @Column(nullable = false)
    private Long userId;
    @Column
    private Long slotId;
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;
    private LocalDateTime approvedAt;
    protected Payment() {}
    public Payment(String paymentKey, String orderId, int amount, Long userId, Long slotId) {
        this.paymentKey = paymentKey; this.orderId = orderId; this.amount = amount; this.userId = userId; this.slotId = slotId;
        this.status = PaymentStatus.APPROVED; this.approvedAt = LocalDateTime.now();
    }
    public String getPaymentKey() { return paymentKey; }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getSlotId() { return slotId; }
    public String getOrderId() { return orderId; }
    public int getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public void cancel() { this.status = PaymentStatus.CANCELLED; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
}
