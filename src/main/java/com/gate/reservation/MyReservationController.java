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

    public MyReservationController(ReservationService service) { this.service = service; }

    @GetMapping("/my-reservations")
    public String mine(HttpSession session, Model model) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return "redirect:/login";
        model.addAttribute("reservations", service.findMine(userId));
        return "reservations/mine";
    }

    @PostMapping("/reservations/cancel")
    public String cancel(@RequestParam Long reservationId, HttpSession session, RedirectAttributes attributes) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return "redirect:/login";
        try { service.cancel(reservationId, userId); attributes.addFlashAttribute("message", "예약이 취소되었습니다."); }
        catch (RuntimeException e) { attributes.addFlashAttribute("message", e.getMessage()); }
        return "redirect:/my-reservations";
    }
}
