package com.gate.reservation;
import com.gate.slot.SlotRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
@RestController
public class AdminController {
 private final SlotRepository slots;
 private final String adminEmail;
 public AdminController(SlotRepository slots, @Value("${gate.admin.email:}") String adminEmail){this.slots=slots;this.adminEmail=adminEmail;}
 @GetMapping("/api/admin/summary")
 public Map<String,Object> summary(HttpSession session){
  if(session.getAttribute("userId")==null || adminEmail.isBlank() || !adminEmail.equals(session.getAttribute("userEmail"))) throw new IllegalStateException("관리자 권한이 필요합니다.");
  var all=slots.findAll(); return Map.of("slotCount",all.size(),"capacity",all.stream().mapToInt(s->s.getCapacity()).sum(),"remaining",all.stream().mapToInt(s->s.getRemaining()).sum());
 }
}
