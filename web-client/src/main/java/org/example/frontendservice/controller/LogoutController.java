package org.example.frontendservice.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class LogoutController {

    @GetMapping("/logout/confirm")
    public String logoutPage() {
        return "logout";
    }

    @PostMapping("/logout/confirm")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}