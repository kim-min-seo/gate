package com.gate.reservation;

import jakarta.servlet.http.HttpSession;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PaymentController {
    private final StringRedisTemplate redis;
    private final TossPaymentService toss;
    private final ReservationService reservations;
    private final PaymentRepository payments;

    public PaymentController(StringRedisTemplate redis, TossPaymentService toss, ReservationService reservations, PaymentRepository payments) {
        this.redis = redis; this.toss = toss; this.reservations = reservations; this.payments = payments;
    }

    @GetMapping("/payment/success")
    public String success(@RequestParam String paymentKey, @RequestParam String orderId, @RequestParam int amount,
                          HttpSession session, RedirectAttributes attributes) {
        String raw = redis.opsForValue().get("gate:payment:order:" + orderId);
        Long userId = (Long) session.getAttribute("userId");
        try {
            if (raw == null || userId == null) throw new IllegalStateException("결제 주문이 만료되었거나 로그인 상태가 아닙니다.");
            String[] parts = raw.split("\\|", -1);
            Long slotId = Long.valueOf(parts[0]);
            java.util.List<Long> seatIds = java.util.Arrays.stream(parts[1].split(",")).map(Long::valueOf).toList();
            Long orderUserId = Long.valueOf(parts[2]);
            int expected = seatIds.size() * 10000;
            if (!userId.equals(orderUserId) || amount != expected) throw new IllegalStateException("결제 금액 또는 사용자 정보가 일치하지 않습니다.");
            if (payments.existsByPaymentKey(paymentKey)) throw new IllegalStateException("이미 승인된 결제입니다.");
            toss.confirm(paymentKey, orderId, amount);
            reservations.reserveSeats(slotId, seatIds, userId);
            payments.save(new Payment(paymentKey, orderId, amount, userId, slotId));
            redis.delete("gate:payment:order:" + orderId);
            attributes.addFlashAttribute("message", "테스트 결제가 승인되고 예약이 확정되었습니다.");
        } catch (RuntimeException e) { attributes.addFlashAttribute("message", "결제 승인 실패: " + e.getMessage()); }
        return "redirect:/slots";
    }

    @GetMapping("/payment/fail")
    public String fail(@RequestParam(required = false) String message, RedirectAttributes attributes) {
        attributes.addFlashAttribute("message", "결제가 취소되거나 실패했습니다: " + (message == null ? "사용자 취소" : message));
        return "redirect:/slots";
    }
}
