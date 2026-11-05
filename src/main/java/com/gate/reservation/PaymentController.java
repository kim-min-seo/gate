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
    private final PaymentRecoveryService recovery;

    public PaymentController(StringRedisTemplate redis, TossPaymentService toss, ReservationService reservations, PaymentRepository payments, PaymentRecoveryService recovery) {
        this.redis = redis; this.toss = toss; this.reservations = reservations; this.payments = payments; this.recovery = recovery;
    }

    @GetMapping("/payment/success")
    public String success(@RequestParam String paymentKey, @RequestParam String orderId, @RequestParam int amount,
                          HttpSession session, RedirectAttributes attributes) {
        Long userId = (Long) session.getAttribute("userId");
        if(userId==null){
            attributes.addFlashAttribute("error","로그인 시간이 만료되었습니다. 다시 로그인 후 내 예약에서 결제 상태를 확인해 주세요.");
            return "redirect:/login";
        }
        boolean recorded=false;
        try {
            var existing=payments.findByOrderId(orderId).orElse(null);
            if(existing!=null){
                if(!existing.getUserId().equals(userId)||!existing.getPaymentKey().equals(paymentKey)||existing.getAmount()!=amount)
                    throw new IllegalStateException("결제 정보가 일치하지 않습니다.");
                attributes.addFlashAttribute("message",existing.getStatus()==PaymentStatus.APPROVED ? "이미 완료된 결제입니다." : "이미 취소된 결제입니다.");
                return "redirect:/my-reservations";
            }
            String raw = redis.opsForValue().get("gate:payment:order:" + orderId);
            if(raw==null){
                attributes.addFlashAttribute("message","결제 대기 시간이 만료되었습니다. 내 예약에서 상태를 확인한 뒤 좌석을 다시 선택해 주세요.");
                return "redirect:/slots";
            }
            String[] parts = raw.split("\\|", -1);
            Long slotId = Long.valueOf(parts[0]);
            java.util.List<Long> seatIds = java.util.Arrays.stream(parts[1].split(",")).map(Long::valueOf).toList();
            Long orderUserId = Long.valueOf(parts[2]);
            int expected = seatIds.size() * 10000;
            if (!userId.equals(orderUserId) || amount != expected) throw new IllegalStateException("결제 금액 또는 사용자 정보가 일치하지 않습니다.");
            recovery.prepare(orderId, paymentKey, userId, slotId, amount, seatIds);
            recorded=true;
            reservations.recordPaidOrder(slotId, seatIds, userId, paymentKey, orderId, amount);
            recovery.resolve(orderId);
            redis.delete("gate:payment:order:" + orderId);
            attributes.addFlashAttribute("message", "테스트 결제가 승인되고 예약이 확정되었습니다.");
        } catch (RuntimeException e) { attributes.addFlashAttribute("message", recorded ? "결제 결과를 확인 중입니다. 중복 결제하지 말고 내 예약에서 상태를 확인해 주세요." : "결제 요청을 처리하지 못했습니다. 내 예약에서 상태를 확인하고 다시 시도해 주세요."); }
        return "redirect:/slots";
    }

    @GetMapping("/payment/fail")
    public String fail(@RequestParam(required = false) String message, RedirectAttributes attributes) {
        attributes.addFlashAttribute("message", "결제가 취소되거나 실패했습니다: " + (message == null ? "사용자 취소" : message));
        return "redirect:/slots";
    }
}
