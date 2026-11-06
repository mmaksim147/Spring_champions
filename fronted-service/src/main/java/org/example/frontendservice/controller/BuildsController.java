package org.example.frontendservice.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/builds")
public class BuildsController {

    private final RestTemplate restTemplate;

    @Value("${gateway.url}")
    private String gatewayUrl;

    public BuildsController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping
    public String listBuilds(HttpSession session, Model model) {
        String token = (String) session.getAttribute("token");

        if (token == null) {
            return "redirect:/login";
        }

        try {
            Long userId = extractUserIdFromToken(token);

            if (userId == null) {
                return "redirect:/login";
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ResponseEntity<List> response = restTemplate.exchange(
                    gatewayUrl + "/api/builds?userId=" + userId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    List.class
            );

            model.addAttribute("builds", response.getBody());

        } catch (Exception e) {
            model.addAttribute("error", "Ошибка загрузки сборок");
            model.addAttribute("builds", List.of());
        }

        return "builds";
    }

    @PostMapping("/delete/{id}")
    public String deleteBuild(
            @PathVariable Long id,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        String token = (String) session.getAttribute("token");

        if (token == null) {
            return "redirect:/login";
        }

        try {
            Long userId = extractUserIdFromToken(token);

            if (userId == null) {
                return "redirect:/login";
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            // Сначала проверяем, что сборка принадлежит пользователю.
            ResponseEntity<Map> response = restTemplate.exchange(
                    gatewayUrl + "/api/builds/" + id,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );

            Map<String, Object> build = response.getBody();

            if (build == null
                    || build.get("userId") == null
                    || !userId.equals(
                    Long.valueOf(build.get("userId").toString())
            )) {

                redirectAttributes.addFlashAttribute(
                        "error",
                        "Нельзя удалить чужую сборку."
                );

                return "redirect:/builds";
            }

            restTemplate.exchange(
                    gatewayUrl + "/api/builds/" + id,
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    Void.class
            );

            redirectAttributes.addFlashAttribute(
                    "success",
                    "Сборка успешно удалена."
            );

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Не удалось удалить сборку."
            );
        }

        return "redirect:/builds";
    }

    @PostMapping("/checkout/{id}")
    @SuppressWarnings("unchecked")
    public String checkoutBuild(
            @PathVariable Long id,
            HttpSession session
    ) {
        String token = (String) session.getAttribute("token");

        if (token == null) {
            return "redirect:/login";
        }

        try {
            Long userId = extractUserIdFromToken(token);

            if (userId == null) {
                return "redirect:/login";
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ResponseEntity<Map> buildResponse = restTemplate.exchange(
                    gatewayUrl + "/api/builds/" + id,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );

            Map<String, Object> build = buildResponse.getBody();

            if (build == null
                    || build.get("userId") == null
                    || !userId.equals(
                    Long.valueOf(build.get("userId").toString())
            )) {
                return "redirect:/builds";
            }

            List<Map<String, Object>> buildItems =
                    (List<Map<String, Object>>) build.get("items");

            List<Map<String, Object>> orderItems = new ArrayList<>();

            for (Map<String, Object> item : buildItems) {
                Map<String, Object> component =
                        (Map<String, Object>) item.get("component");

                orderItems.add(Map.of(
                        "componentId", component.get("id"),
                        "quantity", item.get("quantity")
                ));
            }

            Map<String, Object> orderBody = Map.of(
                    "userId", userId,
                    "items", orderItems
            );

            headers.setContentType(MediaType.APPLICATION_JSON);

            restTemplate.exchange(
                    gatewayUrl + "/api/orders",
                    HttpMethod.POST,
                    new HttpEntity<>(orderBody, headers),
                    Void.class
            );

            // Сохраняем прежнюю логику:
            // после успешного заказа сборка удаляется.
            restTemplate.exchange(
                    gatewayUrl + "/api/builds/" + id,
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    Void.class
            );

            return "redirect:/orders?success";

        } catch (Exception e) {
            return "redirect:/builds?error=checkout";
        }
    }

    private Long extractUserIdFromToken(String token) {
        try {
            String[] parts = token.split("\\.");

            if (parts.length < 2) {
                return null;
            }

            String payload = new String(
                    java.util.Base64.getUrlDecoder().decode(parts[1]),
                    StandardCharsets.UTF_8
            );

            java.util.regex.Matcher matcher =
                    java.util.regex.Pattern
                            .compile("\"userId\"\\s*:\\s*(\\d+)")
                            .matcher(payload);

            if (matcher.find()) {
                return Long.parseLong(matcher.group(1));
            }

            return null;

        } catch (Exception e) {
            return null;
        }
    }
}