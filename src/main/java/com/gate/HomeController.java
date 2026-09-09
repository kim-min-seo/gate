package com.gate;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("title", "Gate 선착순 예약 시스템");
        model.addAttribute("status", "서버가 정상적으로 실행 중입니다.");
        return "index";
    }
}
