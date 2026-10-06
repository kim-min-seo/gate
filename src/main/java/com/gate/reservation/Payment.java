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
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;
    private LocalDateTime approvedAt;
    protected Payment() {}
    public Payment(String paymentKey, String orderId, int amount, Long userId) {
        this.paymentKey = paymentKey; this.orderId = orderId; this.amount = amount; this.userId = userId;
        this.status = PaymentStatus.APPROVED; this.approvedAt = LocalDateTime.now();
    }
    public String getPaymentKey() { return paymentKey; }
    public String getOrderId() { return orderId; }
    public int getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
}
