package com.gate.reservation;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Duration;
import java.util.UUID;

@Controller
public class ReservationController {
    private final ReservationService service;
    private final StringRedisTemplate redis;

    public ReservationController(ReservationService service, StringRedisTemplate redis) { this.service = service; this.redis = redis; }

    @GetMapping("/payment")
    public String paymentEntry(RedirectAttributes attributes) {
        attributes.addFlashAttribute("message", "시간대와 좌석을 선택한 뒤 결제를 진행해 주세요.");
        return "redirect:/slots";
    }

    @PostMapping("/payment")
    public String payment(@RequestParam(required=false) Long slotId, @RequestParam(required=false) java.util.List<Long> seatIds, jakarta.servlet.http.HttpSession session, org.springframework.ui.Model model, RedirectAttributes attributes) {
        Long userId = (Long) session.getAttribute("userId"); if (userId == null) return "redirect:/login";
        if (slotId == null) { attributes.addFlashAttribute("message", "날짜와 시간대를 먼저 선택해 주세요."); return "redirect:/slots"; }
        if (seatIds == null || seatIds.isEmpty() || seatIds.size() > 4) {
            attributes.addFlashAttribute("message", "좌석을 1~4개 선택한 뒤 예매해 주세요.");
            return "redirect:/slots/" + slotId + "/seats";
        }
        String orderId = "GATE-" + UUID.randomUUID();
        try { service.holdOrder(slotId, seatIds, userId, orderId); }
        catch (IllegalStateException failure) { attributes.addFlashAttribute("message", failure.getMessage()); return "redirect:/slots/" + slotId + "/seats"; }
        if (session.getAttribute("paymentCustomerKey") == null) session.setAttribute("paymentCustomerKey", UUID.randomUUID().toString());
        model.addAttribute("customerKey", session.getAttribute("paymentCustomerKey"));
        String payload = slotId + "|" + String.join(",", seatIds.stream().map(String::valueOf).toList()) + "|" + userId;
        redis.opsForValue().set("gate:payment:order:" + orderId, payload, Duration.ofMinutes(10));
        model.addAttribute("slot", service.getSlot(slotId)); model.addAttribute("seatIds", seatIds);
        model.addAttribute("orderId", orderId); model.addAttribute("amount", seatIds.size() * 10000);
        return "payment/checkout";
    }

    @PostMapping("/reservations/auto")
    public String auto(@RequestParam Long slotId, @RequestParam(defaultValue="1") int count, jakarta.servlet.http.HttpSession session, org.springframework.ui.Model model, RedirectAttributes attributes) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return "redirect:/login";
        try {
            var ids = service.selectAvailableSeats(slotId, count);
            return payment(slotId, ids, session, model, attributes);
        }
        catch (IllegalStateException e) { attributes.addFlashAttribute("message", e.getMessage()); }
        return "redirect:/slots";
    }
}
