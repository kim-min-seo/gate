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
    public String reserve(@RequestParam Long slotId, @RequestParam(required=false) Long seatId, jakarta.servlet.http.HttpSession session,
                          RedirectAttributes attributes) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) { attributes.addFlashAttribute("message", "로그인 후 예약할 수 있습니다."); return "redirect:/login"; }
        try {
            if (seatId == null) service.reserve(slotId, userId); else service.reserveSeat(slotId, seatId, userId);
            attributes.addFlashAttribute("message", "예약이 접수되었습니다.");
        } catch (IllegalStateException e) {
            attributes.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/slots";
    }
}
