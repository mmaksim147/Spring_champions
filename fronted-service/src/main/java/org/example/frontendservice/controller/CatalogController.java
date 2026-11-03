package org.example.frontendservice.controller;

import jakarta.servlet.http.HttpSession;
import org.example.frontendservice.dto.ProductDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestTemplate;

@Controller
public class CatalogController {

    private final RestTemplate restTemplate;

    @Value("${gateway.url}")
    private String gatewayUrl;

    public CatalogController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping("/catalog")
    public String catalog(HttpSession session, Model model) {
        String token = (String) session.getAttribute("token");
        if (token == null) {
            return "redirect:/login";
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);

            ResponseEntity<ProductDto[]> response = restTemplate.exchange(
                    gatewayUrl + "/api/products",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    ProductDto[].class
            );

            model.addAttribute("products", response.getBody());
            model.addAttribute("username", session.getAttribute("username"));
        } catch (Exception e) {
            model.addAttribute("error", "Ошибка загрузки каталога: " + e.getMessage());
        }
        return "catalog";
    }
}