package org.example.frontendservice.config;

import jakarta.servlet.http.HttpSession;
import org.example.frontendservice.dto.CartDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.client.RestTemplate;

@ControllerAdvice
public class GlobalModelAdvice {

    private final RestTemplate restTemplate;

    @Value("${gateway.url}")
    private String gatewayUrl;

    public GlobalModelAdvice(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @ModelAttribute
    public void addGlobalAttributes(HttpSession session, Model model) {
        model.addAttribute("cartCount", 0);

        Object tokenValue = session.getAttribute("token");
        Object userIdValue = session.getAttribute("userId");

        if (!(tokenValue instanceof String token)
                || token.isBlank()) {
            return;
        }

        if (!(userIdValue instanceof Number number)) {
            return;
        }

        long userId = number.longValue();

        if (userId <= 0
                || number.doubleValue() != (double) userId) {
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ResponseEntity<CartDto> response = restTemplate.exchange(
                    gatewayUrl + "/api/cart/" + userId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    CartDto.class
            );

            CartDto cart = response.getBody();

            if (cart != null && cart.getItems() != null) {
                model.addAttribute(
                        "cartCount",
                        cart.getItems().size()
                );
            }
        } catch (Exception e) {
            // При недоступности gateway оставляем cartCount = 0.
        }
    }
}