
package org.example.frontendservice.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/components")
public class AdminComponentController {

    private final RestTemplate restTemplate;

    @Value("${gateway.url}")
    private String gatewayUrl;

    public AdminComponentController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private boolean isAdmin(HttpSession session) {
        Object token = session.getAttribute("token");
        Object roles = session.getAttribute("roles");

        return token instanceof String tokenValue
                && !tokenValue.isBlank()
                && roles instanceof List<?> roleList
                && roleList.contains("ROLE_ADMIN");
    }

    private String accessRedirect(HttpSession session) {
        Object token = session.getAttribute("token");

        if (!(token instanceof String tokenValue)
                || tokenValue.isBlank()) {
            return "redirect:/login";
        }

        return "redirect:/catalog";
    }

    private HttpHeaders authHeaders(HttpSession session) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(
                (String) session.getAttribute("token")
        );
        return headers;
    }

    @GetMapping
    public String list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            HttpSession session,
            Model model
    ) {
        if (!isAdmin(session)) {
            return accessRedirect(session);
        }

        try {
            HttpHeaders headers = authHeaders(session);

            UriComponentsBuilder urlBuilder =
                    UriComponentsBuilder.fromUriString(
                                    gatewayUrl + "/api/components"
                            )
                            .queryParam("page", page)
                            .queryParam("size", size);

            if (search != null && !search.isBlank()) {
                urlBuilder.queryParam("search", search);
            }

            String url = urlBuilder
                    .build()
                    .encode()
                    .toUriString();

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );

            Map<?, ?> body = response.getBody();

            if (body != null) {
                Object components = body.get("content");
                Object totalPages = body.get("totalPages");

                model.addAttribute(
                        "components",
                        components != null ? components : List.of()
                );

                model.addAttribute(
                        "totalPages",
                        totalPages instanceof Number number
                                ? number.intValue()
                                : 0
                );

            } else {
                model.addAttribute("components", List.of());
                model.addAttribute("totalPages", 0);
            }

            model.addAttribute("currentPage", page);
            model.addAttribute("search", search);

        } catch (Exception e) {
            model.addAttribute("components", List.of());
            model.addAttribute("totalPages", 0);
            model.addAttribute("currentPage", page);
            model.addAttribute("search", search);
            model.addAttribute(
                    "error",
                    "Не удалось загрузить компоненты."
            );
        }

        return "admin/components";
    }

    @PostMapping("/create")
    public String create(
            @RequestParam String name,
            @RequestParam String manufacturer,
            @RequestParam BigDecimal price,
            @RequestParam Integer stockQuantity,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        if (!isAdmin(session)) {
            return accessRedirect(session);
        }

        try {
            HttpHeaders headers = authHeaders(session);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "name", name,
                    "manufacturer", manufacturer,
                    "price", price,
                    "stockQuantity", stockQuantity,
                    "category", Map.of("id", 1)
            );

            restTemplate.postForEntity(
                    gatewayUrl + "/api/components",
                    new HttpEntity<>(body, headers),
                    Void.class
            );

            redirectAttributes.addFlashAttribute(
                    "success",
                    "Компонент успешно добавлен."
            );

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Не удалось добавить компонент."
            );
        }

        return "redirect:/admin/components";
    }

    @PostMapping("/update")
    public String update(
            @RequestParam Long id,
            @RequestParam String name,
            @RequestParam String manufacturer,
            @RequestParam BigDecimal price,
            @RequestParam Integer stockQuantity,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        if (!isAdmin(session)) {
            return accessRedirect(session);
        }

        if (id == null || id <= 0) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Некорректный ID компонента."
            );
            return "redirect:/admin/components";
        }

        try {
            HttpHeaders headers = authHeaders(session);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "name", name,
                    "manufacturer", manufacturer,
                    "price", price,
                    "stockQuantity", stockQuantity
            );

            restTemplate.exchange(
                    gatewayUrl + "/api/components/" + id,
                    HttpMethod.PUT,
                    new HttpEntity<>(body, headers),
                    Void.class
            );

            redirectAttributes.addFlashAttribute(
                    "success",
                    "Компонент успешно обновлён."
            );

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Не удалось обновить компонент."
            );
        }

        return "redirect:/admin/components";
    }

    @PostMapping("/delete/{id}")
    public String delete(
            @PathVariable Long id,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        if (!isAdmin(session)) {
            return accessRedirect(session);
        }

        if (id == null || id <= 0) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Некорректный ID компонента."
            );
            return "redirect:/admin/components";
        }

        try {
            HttpHeaders headers = authHeaders(session);

            restTemplate.exchange(
                    gatewayUrl + "/api/components/" + id,
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    Void.class
            );

            redirectAttributes.addFlashAttribute(
                    "success",
                    "Компонент успешно удалён."
            );

        } catch (HttpClientErrorException.Conflict e) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Нельзя удалить компонент: он используется "
                            + "в сохранённых сборках или других данных."
            );

        } catch (HttpClientErrorException.NotFound e) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Компонент не найден."
            );

        } catch (HttpClientErrorException.Forbidden e) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Недостаточно прав для удаления компонента."
            );

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Не удалось удалить компонент. "
                            + "Проверьте работу сервисов Colizant."
            );
        }

        return "redirect:/admin/components";
    }
}
