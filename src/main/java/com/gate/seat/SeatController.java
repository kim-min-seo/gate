package com.gate.seat;

import com.gate.slot.SlotRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class SeatController {
    private final SlotRepository slots; private final SeatRepository seats;
    private final com.gate.reservation.ReservationService reservations;
    public SeatController(SlotRepository slots, SeatRepository seats, com.gate.reservation.ReservationService reservations) { this.slots = slots; this.seats = seats;this.reservations=reservations; }
    @GetMapping("/slots/{slotId}/seats")
    public String seats(@PathVariable Long slotId, Model model) {
        var slot = slots.findById(slotId).orElseThrow();
        if(!slot.getStartsAt().isAfter(java.time.LocalDateTime.now())) return "redirect:/slots";
        reservations.ensureSeatInventory(slotId);
        model.addAttribute("slot", slot); model.addAttribute("seats", seats.findAllBySlotOrderByLabelAsc(slot));
        return "seats/list";
    }
}
