package com.gate.slot;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.LocalDate;

@Controller
@RequestMapping("/slots")
public class SlotController {
    private final SlotService slotService;

    public SlotController(SlotService slotService) {
        this.slotService = slotService;
    }

    @GetMapping
    public String list(@RequestParam(required=false) String date, Model model) {
        var now = java.time.LocalDateTime.now();
        var all = slotService.findAll().stream().filter(s -> s.getStartsAt().isAfter(now) && s.getStartsAt().toLocalDate().isBefore(LocalDate.now().plusMonths(6))).toList();
        var dates = all.stream().map(s -> s.getStartsAt().toLocalDate()).distinct().sorted().toList();
        LocalDate selected = dates.isEmpty() ? LocalDate.now() : dates.getFirst();
        if (date != null) {
            try { var requested = LocalDate.parse(date); if (dates.contains(requested)) selected = requested; }
            catch (java.time.format.DateTimeParseException ignored) { model.addAttribute("message", "올바른 예약 날짜를 선택해 주세요."); }
        }
        final LocalDate selectedDate = selected;
        model.addAttribute("slots", all.stream().filter(s -> s.getStartsAt().toLocalDate().equals(selectedDate)).toList());
        model.addAttribute("dates", dates);
        model.addAttribute("selectedDate", selected);
        return "slots/list";
    }
}
