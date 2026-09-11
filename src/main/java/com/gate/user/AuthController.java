package com.gate.user;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {
    private final UserRepository users;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthController(UserRepository users) { this.users = users; }

    @GetMapping("/signup")
    public String signupForm() { return "auth/signup"; }

    @PostMapping("/signup")
    public String signup(@RequestParam String email, @RequestParam String password,
                         HttpSession session, RedirectAttributes attributes) {
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || password.length() < 8) {
            attributes.addFlashAttribute("error", "올바른 이메일과 8자 이상 비밀번호를 입력하세요."); return "redirect:/signup";
        }
        if (users.existsByEmail(email)) {
            attributes.addFlashAttribute("error", "이미 가입된 이메일입니다.");
            return "redirect:/signup";
        }
        User user = users.save(new User(email, encoder.encode(password)));
        session.setAttribute("userId", user.getId());
        session.setAttribute("userEmail", user.getEmail());
        return "redirect:/slots";
    }

    @GetMapping("/login")
    public String loginForm() { return "auth/login"; }

    @PostMapping("/login")
    public String login(@RequestParam String email, @RequestParam String password,
                        HttpSession session, RedirectAttributes attributes) {
        User user = users.findByEmail(email).orElse(null);
        if (user == null || !encoder.matches(password, user.getPasswordHash())) {
            attributes.addFlashAttribute("error", "이메일 또는 비밀번호가 올바르지 않습니다.");
            return "redirect:/login";
        }
        session.setAttribute("userId", user.getId());
        session.setAttribute("userEmail", user.getEmail());
        return "redirect:/slots";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) { session.invalidate(); return "redirect:/login"; }
}
