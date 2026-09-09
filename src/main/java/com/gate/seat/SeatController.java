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
    public SeatController(SlotRepository slots, SeatRepository seats) { this.slots = slots; this.seats = seats; }
    @GetMapping("/slots/{slotId}/seats")
    public String seats(@PathVariable Long slotId, Model model) {
        var slot = slots.findById(slotId).orElseThrow();
        model.addAttribute("slot", slot); model.addAttribute("seats", seats.findAllBySlotOrderByLabelAsc(slot));
        return "seats/list";
    }
    @PostMapping("/payment")
    public String payment(@RequestParam Long slotId, @RequestParam Long seatId, Model model) {
        var slot = slots.findById(slotId).orElseThrow(); var seat = seats.findById(seatId).orElseThrow();
        if (seat.isReserved()) return "redirect:/slots/" + slotId + "/seats";
        model.addAttribute("slot", slot); model.addAttribute("seat", seat); return "payment/checkout";
    }
}
