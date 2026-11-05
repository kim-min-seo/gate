package com.gate.user;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {
    private final UserRepository users;
    private final LoginAttemptLimiter limiter;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final String dummyHash=encoder.encode("non-user-password-check");

    public AuthController(UserRepository users, LoginAttemptLimiter limiter) { this.users = users; this.limiter=limiter; }

    @GetMapping("/signup")
    public String signupForm() { return "auth/signup"; }

    @PostMapping("/signup")
    public String signup(@RequestParam String email, @RequestParam String password,
                         HttpSession session, jakarta.servlet.http.HttpServletRequest request, RedirectAttributes attributes) {
        email=email.trim();
        if (email.length()>254 || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || password.length() < 8 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            attributes.addFlashAttribute("error", "올바른 이메일과 8자 이상 비밀번호를 입력하세요."); return "redirect:/signup";
        }
        if (users.existsByEmail(email)) {
            attributes.addFlashAttribute("error", "이미 가입된 이메일입니다.");
            return "redirect:/signup";
        }
        User user;
        try {user = users.save(new User(email, encoder.encode(password)));}
        catch(org.springframework.dao.DataIntegrityViolationException duplicate){attributes.addFlashAttribute("error","가입 정보를 확인해 주세요. 이미 가입된 이메일일 수 있습니다.");return "redirect:/signup";}
        request.changeSessionId();
        session.setAttribute("userId", user.getId());
        session.setAttribute("userEmail", user.getEmail());
        return "redirect:/slots";
    }

    @GetMapping("/login")
    public String loginForm() { return "auth/login"; }

    @PostMapping("/login")
    public String login(@RequestParam String email, @RequestParam String password,
                        HttpSession session, jakarta.servlet.http.HttpServletRequest request, RedirectAttributes attributes) {
        email=email.trim();
        if(email.length()>254 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72){
            attributes.addFlashAttribute("error","이메일 또는 비밀번호가 올바르지 않습니다.");return "redirect:/login";
        }
        try {
            if(!limiter.allow(email,request.getRemoteAddr())){
                attributes.addFlashAttribute("error","로그인 시도가 많습니다. 10분 후 다시 시도해 주세요.");return "redirect:/login";
            }
        } catch(org.springframework.dao.DataAccessException unavailable){
            attributes.addFlashAttribute("error","로그인을 잠시 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.");return "redirect:/login";
        }
        User user = users.findByEmail(email).orElse(null);
        boolean passwordMatches=encoder.matches(password,user==null ? dummyHash : user.getPasswordHash());
        if (user == null || !passwordMatches) {
            attributes.addFlashAttribute("error", "이메일 또는 비밀번호가 올바르지 않습니다.");
            return "redirect:/login";
        }
        try {limiter.success(email,request.getRemoteAddr());}
        catch(org.springframework.dao.DataAccessException unavailable){attributes.addFlashAttribute("error","로그인을 잠시 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.");return "redirect:/login";}
        request.changeSessionId();
        session.setAttribute("userId", user.getId());
        session.setAttribute("userEmail", user.getEmail());
        return "redirect:/slots";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) { session.invalidate(); return "redirect:/login"; }
}
