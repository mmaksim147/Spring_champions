package org.example.frontendservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.example.frontendservice.dto.OrderDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Controller
public class OrderController {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gateway.url}")
    private String gatewayUrl;

    public OrderController(
            RestTemplate restTemplate,
            ObjectMapper objectMapper
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/orders")
    public String ordersPage(HttpSession session, Model model) {
        String token = getToken(session);

        if (token == null) {
            return "redirect:/login";
        }

        Long userId = getUserId(token);

        if (userId == null) {
            model.addAttribute("error", "Не удалось определить пользователя.");
            model.addAttribute("orders", List.of());
            model.addAttribute("componentNames", Map.of());
            return "orders";
        }

        try {
            HttpHeaders headers = createHeaders(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<OrderDto[]> response = restTemplate.exchange(
                    gatewayUrl + "/api/orders/user/" + userId,
                    HttpMethod.GET,
                    entity,
                    OrderDto[].class
            );

            OrderDto[] orders = response.getBody();

            if (orders == null) {
                orders = new OrderDto[0];
            }

            model.addAttribute("orders", Arrays.asList(orders));

            Map<Long, String> componentNames = new HashMap<>();

            for (OrderDto order : orders) {
                if (order == null || order.getItems() == null) {
                    continue;
                }

                for (var item : order.getItems()) {
                    if (item == null) {
                        continue;
                    }

                    Long componentId = item.getComponentId();

                    if (componentId == null
                            || componentNames.containsKey(componentId)) {
                        continue;
                    }

                    try {
                        ResponseEntity<Map> componentResponse =
                                restTemplate.exchange(
                                        gatewayUrl
                                                + "/api/components/"
                                                + componentId,
                                        HttpMethod.GET,
                                        entity,
                                        Map.class
                                );

                        Map<?, ?> component =
                                componentResponse.getBody();

                        if (component != null
                                && component.get("name") != null) {
                            componentNames.put(
                                    componentId,
                                    component.get("name").toString()
                            );
                        } else {
                            componentNames.put(
                                    componentId,
                                    "Комплектующее ID " + componentId
                            );
                        }

                    } catch (Exception e) {
                        componentNames.put(
                                componentId,
                                "Комплектующее ID " + componentId
                        );
                    }
                }
            }

            model.addAttribute("componentNames", componentNames);

            Object roles = session.getAttribute("roles");

            model.addAttribute(
                    "isAdmin",
                    roles instanceof List<?> roleList
                            && roleList.contains("ROLE_ADMIN")
            );

        } catch (Exception e) {
            model.addAttribute("error", "Ошибка загрузки заказов.");
            model.addAttribute("orders", List.of());
            model.addAttribute("componentNames", Map.of());
        }

        return "orders";
    }

    @PostMapping("/orders/{orderId}/items/{itemId}/quantity")
    public String updateOrderQuantity(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @RequestParam Integer quantity,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        String token = getToken(session);

        if (token == null) {
            return "redirect:/login";
        }

        Long userId = getUserId(token);

        if (userId == null || orderId == null || orderId <= 0
                || itemId == null || itemId <= 0
                || quantity == null || quantity < 1 || quantity > 99) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    "Некорректные данные изменения заказа."
            );

            return "redirect:/orders";
        }

        try {
            String url = gatewayUrl
                    + "/api/orders/" + orderId
                    + "/items/" + itemId
                    + "?userId=" + userId
                    + "&quantity=" + quantity;

            restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    new HttpEntity<>(createHeaders(token)),
                    Map.class
            );

            redirectAttributes.addFlashAttribute(
                    "success",
                    "Количество комплектующего изменено."
            );

        } catch (HttpStatusCodeException e) {
            String message = "Не удалось изменить количество в заказе.";

            try {
                JsonNode json = objectMapper.readTree(
                        e.getResponseBodyAsString()
                );

                if (json.hasNonNull("error")) {
                    message = json.get("error").asText();
                }
            } catch (Exception ignored) {
                // Показываем стандартную ошибку.
            }

            redirectAttributes.addFlashAttribute("error", message);

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Не удалось изменить количество в заказе."
            );
        }

        return "redirect:/orders";
    }

    @PostMapping("/orders/create")
    public String createOrder(
            @RequestParam Long componentId,
            @RequestParam Integer quantity,
            HttpSession session
    ) {
        String token = getToken(session);

        if (token == null) {
            return "redirect:/login";
        }

        Long userId = getUserId(token);

        if (userId == null || componentId == null || componentId <= 0
                || quantity == null || quantity < 1 || quantity > 99) {
            return "redirect:/catalog?error";
        }

        try {
            HttpHeaders headers = createHeaders(token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "userId", userId,
                    "items", List.of(
                            Map.of(
                                    "componentId", componentId,
                                    "quantity", quantity
                            )
                    )
            );

            restTemplate.exchange(
                    gatewayUrl + "/api/orders",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Void.class
            );

            return "redirect:/orders?success";

        } catch (Exception e) {
            return "redirect:/catalog?error";
        }
    }

    private HttpHeaders createHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private String getToken(HttpSession session) {
        Object value = session.getAttribute("token");

        if (value instanceof String token && !token.isBlank()) {
            return token;
        }

        return null;
    }

    private Long getUserId(String token) {
        try {
            String[] parts = token.split("\\.");

            if (parts.length < 2) {
                return null;
            }

            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);

            JsonNode json = objectMapper.readTree(
                    new String(decoded, StandardCharsets.UTF_8)
            );

            JsonNode userIdNode = json.get("userId");

            if (userIdNode == null
                    || !userIdNode.canConvertToLong()) {
                return null;
            }

            long userId = userIdNode.asLong();

            return userId > 0 ? userId : null;

        } catch (Exception e) {
            return null;
        }
    }
}