package com.gate.reservation;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class MyReservationController {
    private final ReservationService service;
    private final PaymentRepository payments;
    private final PaymentRecoveryRepository recovery;
    private final PaymentRecoveryService recoveryService;

    public MyReservationController(ReservationService service, PaymentRepository payments, PaymentRecoveryRepository recovery, PaymentRecoveryService recoveryService) { this.service = service; this.payments = payments; this.recovery = recovery; this.recoveryService = recoveryService; }

    @GetMapping("/my-reservations")
    public String mine(HttpSession session, Model model) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return "redirect:/login";
        model.addAttribute("reservations", service.findMine(userId));
        model.addAttribute("recoveries", recovery.findByUserIdAndStatusIn(userId, java.util.List.of("PENDING", "MANUAL_REVIEW")));
        model.addAttribute("payments", payments.findByUserIdOrderByApprovedAtDesc(userId));
        return "reservations/mine";
    }

    @PostMapping("/reservations/cancel")
    public String cancel(@RequestParam Long reservationId, HttpSession session, RedirectAttributes attributes) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return "redirect:/login";
        try { recoveryService.prepareCancellation(reservationId, userId); service.cancel(reservationId, userId); attributes.addFlashAttribute("message", "예약이 취소되었습니다."); }
        catch (RuntimeException e) { attributes.addFlashAttribute("message", "취소 처리를 확인하지 못했습니다. 결제 확인 중 안내가 있으면 자동 재조회됩니다."); }
        return "redirect:/my-reservations";
    }
}
