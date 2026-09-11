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
        var all = slotService.findAll();
        LocalDate selected = date == null ? (all.isEmpty() ? LocalDate.now() : all.get(0).getStartsAt().toLocalDate()) : LocalDate.parse(date);
        model.addAttribute("slots", all.stream().filter(s -> s.getStartsAt().toLocalDate().equals(selected)).toList());
        model.addAttribute("dates", all.stream().map(s -> s.getStartsAt().toLocalDate()).distinct().sorted().toList());
        model.addAttribute("selectedDate", selected);
        return "slots/list";
    }
}
