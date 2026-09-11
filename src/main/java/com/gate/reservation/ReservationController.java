package com.gate.reservation;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ReservationController {
    private final ReservationService service;

    public ReservationController(ReservationService service) { this.service = service; }

    @PostMapping("/reservations")
    public String reserve(@RequestParam Long slotId, @RequestParam(required=false) Long seatId, @RequestParam(required=false) java.util.List<Long> seatIds, @RequestParam(defaultValue="card") String paymentMethod, @RequestParam(required=false) String cardNumber, @RequestParam(required=false) String expiry, @RequestParam(required=false) String cvc, @RequestParam(required=false) String phoneNumber, @RequestParam(required=false) String depositor, jakarta.servlet.http.HttpSession session,
                          RedirectAttributes attributes) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) { attributes.addFlashAttribute("message", "로그인 후 예약할 수 있습니다."); return "redirect:/login"; }
        try {
            if ("card".equals(paymentMethod) && (cardNumber == null || !cardNumber.replaceAll("\\s", "").matches("\\d{16}") || expiry == null || !expiry.matches("(0[1-9]|1[0-2])/\\d{2}") || cvc == null || !cvc.matches("\\d{3}"))) throw new IllegalStateException("카드 정보를 올바르게 입력하세요.");
            if ("phone".equals(paymentMethod) && (phoneNumber == null || !phoneNumber.replaceAll("[- ]", "").matches("01[0-9]\\d{7,8}"))) throw new IllegalStateException("휴대폰 번호를 올바르게 입력하세요.");
            if ("transfer".equals(paymentMethod) && (depositor == null || depositor.isBlank())) throw new IllegalStateException("입금자명을 입력하세요.");
            if (seatIds != null && !seatIds.isEmpty()) service.reserveSeats(slotId, seatIds, userId);
            else { Reservation reservation = seatId == null ? service.reserve(slotId, userId) : service.reserveSeat(slotId, seatId, userId); reservation.confirm(); }
            attributes.addFlashAttribute("message", "예약이 접수되었습니다.");
        } catch (IllegalStateException e) {
            attributes.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/slots";
    }

    @PostMapping("/payment")
    public String payment(@RequestParam Long slotId, @RequestParam java.util.List<Long> seatIds, jakarta.servlet.http.HttpSession session, org.springframework.ui.Model model) {
        Long userId = (Long) session.getAttribute("userId"); if (userId == null) return "redirect:/login";
        if (seatIds.size() < 1 || seatIds.size() > 4) return "redirect:/slots/" + slotId + "/seats";
        model.addAttribute("slot", service.getSlot(slotId)); model.addAttribute("seatIds", seatIds); return "payment/checkout";
    }

    @PostMapping("/reservations/auto")
    public String auto(@RequestParam Long slotId, @RequestParam(defaultValue="1") int count, jakarta.servlet.http.HttpSession session, RedirectAttributes attributes) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return "redirect:/login";
        try { for(int i=0;i<Math.min(4, Math.max(1,count));i++) service.autoReserve(slotId, userId); attributes.addFlashAttribute("message", "좌석이 자동 배정되었습니다."); }
        catch (IllegalStateException e) { attributes.addFlashAttribute("message", e.getMessage()); }
        return "redirect:/slots";
    }
}
