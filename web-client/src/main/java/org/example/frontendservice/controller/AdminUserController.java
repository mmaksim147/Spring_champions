package org.example.frontendservice.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final RestTemplate restTemplate;

    @Value("${gateway.url}")
    private String gatewayUrl;

    public AdminUserController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private boolean isAdmin(HttpSession session) {
        Object token = session.getAttribute("token");
        Object roles = session.getAttribute("roles");

        if (!(token instanceof String)
                || ((String) token).isBlank()) {
            return false;
        }

        if (!(roles instanceof List<?> roleList)) {
            return false;
        }

        return roleList.contains("ROLE_ADMIN");
    }

    private String accessRedirect(HttpSession session) {
        if (session.getAttribute("token") == null) {
            return "redirect:/login";
        }
        return "redirect:/catalog";
    }

    @GetMapping
    public String list(HttpSession session, Model model) {

        if (!isAdmin(session)) {
            return accessRedirect(session);
        }

        try {
            String token = (String) session.getAttribute("token");

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ResponseEntity<List> response = restTemplate.exchange(
                    gatewayUrl + "/api/admin/users",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    List.class
            );

            model.addAttribute("users", response.getBody());

        } catch (Exception e) {
            model.addAttribute("error", "Ошибка загрузки");
        }

        return "admin/users";
    }

    @PostMapping({"/delete/{id}", "/{id}/delete"})
    public String delete(@PathVariable Long id, HttpSession session) {

        if (!isAdmin(session)) {
            return accessRedirect(session);
        }

        if (id == null || id <= 0) {
            return "redirect:/admin/users?error";
        }

        try {
            String token = (String) session.getAttribute("token");

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            restTemplate.exchange(
                    gatewayUrl + "/api/admin/users/" + id,
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    Void.class
            );

        } catch (Exception e) {
            return "redirect:/admin/users?error";
        }

        return "redirect:/admin/users";
    }

    @PostMapping({"/role/{id}", "/{id}/role"})
    public String changeRole(
            @PathVariable Long id,
            @RequestParam String role,
            HttpSession session) {

        if (!isAdmin(session)) {
            return accessRedirect(session);
        }

        if (id == null || id <= 0
                || role == null || role.isBlank()) {
            return "redirect:/admin/users?error";
        }

        String fullRole = role.startsWith("ROLE_")
                ? role
                : "ROLE_" + role;

        if (!List.of("ROLE_USER", "ROLE_ADMIN").contains(fullRole)) {
            return "redirect:/admin/users?error";
        }

        try {
            String token = (String) session.getAttribute("token");

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "roles", List.of(fullRole)
            );

            restTemplate.exchange(
                    gatewayUrl + "/api/admin/users/" + id + "/roles",
                    HttpMethod.PUT,
                    new HttpEntity<>(body, headers),
                    Void.class
            );

        } catch (Exception e) {
            return "redirect:/admin/users?error";
        }

        return "redirect:/admin/users";
    }
}