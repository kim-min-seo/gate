package com.gate.reservation;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QueueController {
    private final ReservationService service;
    public QueueController(ReservationService service) { this.service = service; }
    @GetMapping("/api/queue/{slotId}")
    public String position(@PathVariable Long slotId, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return "로그인이 필요합니다.";
        Long position = service.queuePosition(slotId, userId);
        return position == null ? "대기열에 없습니다." : "현재 대기 순번: " + position;
    }
}
