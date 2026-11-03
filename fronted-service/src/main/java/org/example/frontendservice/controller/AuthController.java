package org.example.frontendservice.controller;

import jakarta.servlet.http.HttpSession;
import org.example.frontendservice.dto.AuthRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Controller
public class AuthController {

    private final RestTemplate restTemplate;

    @Value("${gateway.url}")
    private String gatewayUrl;

    public AuthController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping("/")
    public String index(HttpSession session) {
        if (session.getAttribute("token") == null) {
            return "redirect:/login";
        }
        return "redirect:/catalog";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        HttpSession session,
                        Model model) {
        try {
            AuthRequest request = new AuthRequest(username, password);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    gatewayUrl + "/api/auth/login",
                    request,
                    Map.class
            );

            if (response.getBody() != null) {
                session.setAttribute("token", response.getBody().get("token"));
                session.setAttribute("username", response.getBody().get("username"));
                session.setAttribute("roles", response.getBody().get("roles"));
                return "redirect:/catalog";
            }
            model.addAttribute("error", "Неверный ответ от сервера");
        } catch (Exception e) {
            model.addAttribute("error", "Неверный логин или пароль");
        }
        return "login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username,
                           @RequestParam String email,
                           @RequestParam String password,
                           Model model) {
        try {
            Map<String, String> body = Map.of(
                    "username", username,
                    "email", email,
                    "password", password
            );
            restTemplate.postForEntity(
                    gatewayUrl + "/api/auth/register",
                    body,
                    Map.class
            );
            return "redirect:/login?registered";
        } catch (Exception e) {
            model.addAttribute("error", "Ошибка регистрации: " + e.getMessage());
            return "register";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}