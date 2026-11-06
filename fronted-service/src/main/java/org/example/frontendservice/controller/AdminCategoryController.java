package org.example.frontendservice.controller;

import jakarta.servlet.http.HttpSession;
import org.example.frontendservice.dto.CategoryDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/categories")
public class AdminCategoryController {

    private final RestTemplate restTemplate;

    @Value("${gateway.url}")
    private String gatewayUrl;

    public AdminCategoryController(RestTemplate restTemplate) {
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

            ResponseEntity<CategoryDto[]> response =
                    restTemplate.exchange(
                            gatewayUrl + "/api/categories",
                            HttpMethod.GET,
                            new HttpEntity<>(headers),
                            CategoryDto[].class
                    );

            CategoryDto[] categories = response.getBody();

            model.addAttribute(
                    "categories",
                    categories == null
                            ? List.of()
                            : Arrays.asList(categories)
            );

        } catch (Exception e) {
            model.addAttribute("error", "Ошибка загрузки");
        }

        return "admin/categories";
    }

    @PostMapping("/create")
    public String create(
            @RequestParam String name,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!isAdmin(session)) {
            return accessRedirect(session);
        }

        if (name == null || name.isBlank()) {
            redirectAttributes.addAttribute(
                    "error",
                    "Название категории не может быть пустым"
            );
            return "redirect:/admin/categories";
        }

        try {
            String token = (String) session.getAttribute("token");

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            restTemplate.postForEntity(
                    gatewayUrl + "/api/categories",
                    new HttpEntity<>(
                            Map.of("name", name.trim()),
                            headers
                    ),
                    Void.class
            );

        } catch (Exception e) {
            redirectAttributes.addAttribute(
                    "error",
                    "Не удалось добавить категорию. Возможно, она уже существует."
            );

            return "redirect:/admin/categories";
        }

        return "redirect:/admin/categories";
    }

    @PostMapping("/delete/{id}")
    public String delete(
            @PathVariable Long id,
            HttpSession session) {

        if (!isAdmin(session)) {
            return accessRedirect(session);
        }

        if (id == null || id <= 0) {
            return "redirect:/admin/categories?error";
        }

        try {
            String token = (String) session.getAttribute("token");

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            restTemplate.exchange(
                    gatewayUrl + "/api/categories/" + id,
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    Void.class
            );

        } catch (Exception e) {
            return "redirect:/admin/categories?error";
        }

        return "redirect:/admin/categories";
    }
}