package org.example.frontendservice.controller;

import jakarta.servlet.http.HttpSession;
import org.example.frontendservice.dto.OrderDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@Controller
@RequestMapping("/admin/orders")
public class AdminOrderController {

    private final RestTemplate restTemplate;

    @Value("${gateway.url}")
    private String gatewayUrl;

    public AdminOrderController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping
    public String listOrders(HttpSession session, Model model) {
        Object tokenValue = session.getAttribute("token");

        if (!(tokenValue instanceof String token) || token.isBlank()) {
            return "redirect:/login";
        }

        if (!isAdmin(session.getAttribute("roles"))) {
            return "redirect:/catalog";
        }

        model.addAttribute("orders", List.of());

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ResponseEntity<OrderDto[]> response = restTemplate.exchange(
                    gatewayUrl + "/api/orders",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    OrderDto[].class
            );

            OrderDto[] body = response.getBody();

            model.addAttribute(
                    "orders",
                    body == null ? List.of() : Arrays.asList(body)
            );

        } catch (Exception e) {
            model.addAttribute(
                    "error",
                    "Не удалось загрузить заказы. Проверьте доступность backend."
            );
        }

        return "admin/orders";
    }

    private boolean isAdmin(Object roles) {
        if (roles instanceof Collection<?> collection) {
            return collection.stream()
                    .anyMatch(role ->
                            "ROLE_ADMIN".equals(String.valueOf(role))
                                    || "ADMIN".equals(String.valueOf(role))
                    );
        }

        return roles != null
                && Arrays.stream(
                        String.valueOf(roles)
                                .replace("[", "")
                                .replace("]", "")
                                .split(",")
                )
                .map(String::trim)
                .anyMatch(role ->
                        "ROLE_ADMIN".equals(role)
                                || "ADMIN".equals(role)
                );
    }
}