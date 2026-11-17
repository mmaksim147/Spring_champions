package org.example.frontendservice.controller;

import jakarta.servlet.http.HttpSession;
import org.example.frontendservice.dto.ProductDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Controller
public class ComponentController {

    private final RestTemplate restTemplate;

    @Value("${gateway.url}")
    private String gatewayUrl;

    public ComponentController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping("/components/{id}")
    public String component(
            @PathVariable Long id,
            HttpSession session,
            Model model
    ) {
        Object tokenValue = session.getAttribute("token");

        if (!(tokenValue instanceof String token) || token.isBlank()) {
            return "redirect:/login";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<ProductDto> response = restTemplate.exchange(
                    gatewayUrl + "/api/components/" + id,
                    HttpMethod.GET,
                    entity,
                    ProductDto.class
            );

            ProductDto product = response.getBody();

            if (product == null) {
                throw new ResponseStatusException(
                        HttpStatusCode.valueOf(404),
                        "Комплектующее не найдено"
                );
            }

            model.addAttribute("product", product);
            model.addAttribute("username", session.getAttribute("username"));

            Object roles = session.getAttribute("roles");
            model.addAttribute(
                    "isAdmin",
                    roles instanceof List<?> roleList
                            && roleList.contains("ROLE_ADMIN")
            );

            return "component";

        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(
                    HttpStatusCode.valueOf(404),
                    "Комплектующее не найдено"
            );
        }
    }
}