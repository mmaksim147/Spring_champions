package org.example.frontendservice.controller;

import jakarta.servlet.http.HttpSession;
import org.example.frontendservice.dto.AuthRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;

@Controller
public class AuthController {

    private final RestTemplate restTemplate;
    @Value("${gateway.url}")
    private String gatewayUrl;

    public AuthController(RestTemplate restTemplate) { this.restTemplate = restTemplate; }

    @GetMapping("/")
    public String index() { return "index"; }

    @GetMapping("/login")
    public String loginPage() { return "login"; }

    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password, HttpSession session, Model model) {
        try {
            AuthRequest request = new AuthRequest(username, password);
            ResponseEntity<Map> response = restTemplate.postForEntity(gatewayUrl + "/api/auth/login", request, Map.class);

            if (response.getBody() != null) {
                session.setAttribute("token", response.getBody().get("token"));
                session.setAttribute("username", response.getBody().get("username"));
                session.setAttribute("roles", response.getBody().get("roles"));
                if (response.getBody().containsKey("userId")) {
                    session.setAttribute("userId", response.getBody().get("userId"));
                }
                return "redirect:/catalog";
            }
        } catch (Exception e) {
            model.addAttribute("error", "Неверный логин или пароль");
        }
        return "login";
    }

    @GetMapping("/register")
    public String registerPage() { return "register"; }


    @PostMapping("/register")
    public String register(
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam String password,
            Model model
    ) {
        username = username.trim();
        email = email.trim();

        if (username.length() < 3 || username.length() > 50) {
            model.addAttribute("error", "Логин должен содержать от 3 до 50 символов.");
            return "register";
        }

        if (email.isBlank() || !email.contains("@")) {
            model.addAttribute("error", "Введите корректный email.");
            return "register";
        }

        if (password.length() < 6) {
            model.addAttribute("error", "Пароль должен содержать минимум 6 символов.");
            return "register";
        }

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

        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            int status = e.getStatusCode().value();

            System.err.println("[REGISTER] HTTP " + status);
            System.err.println("[REGISTER] " + e.getResponseBodyAsString());

            if (status == 409) {
                model.addAttribute("error", "Этот логин уже занят.");
            } else if (status == 400) {
                model.addAttribute("error",
                        "Сервер отклонил данные регистрации. Проверьте логин, email и пароль.");
            } else {
                model.addAttribute("error", "Ошибка сервера при регистрации.");
            }

        } catch (Exception e) {
            System.err.println("[REGISTER] " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());

            model.addAttribute("error", "Не удалось выполнить регистрацию.");
        }

        return "register";
    }


    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}