package com.gate.reservation;
import com.gate.slot.SlotRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
@RestController
public class AdminController {
 private final SlotRepository slots;
 public AdminController(SlotRepository slots){this.slots=slots;}
 @GetMapping("/api/admin/summary")
 public Map<String,Object> summary(HttpSession session){
  if(session.getAttribute("userId")==null) throw new IllegalStateException("로그인이 필요합니다.");
  var all=slots.findAll(); return Map.of("slotCount",all.size(),"capacity",all.stream().mapToInt(s->s.getCapacity()).sum(),"remaining",all.stream().mapToInt(s->s.getRemaining()).sum());
 }
}
