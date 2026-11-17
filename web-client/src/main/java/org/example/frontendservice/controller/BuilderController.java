
package org.example.frontendservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpSession;
import org.example.frontendservice.dto.ProductDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class BuilderController {

    private static final Logger log =
            LoggerFactory.getLogger(BuilderController.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gateway.url}")
    private String gatewayUrl;

    public BuilderController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @GetMapping("/builder")
    public String builderPage(HttpSession session, Model model) {

        String token = (String) session.getAttribute("token");

        if (token == null || token.isBlank()) {
            return "redirect:/login";
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ResponseEntity<Map> response = restTemplate.exchange(
                    gatewayUrl + "/api/components?size=100",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );

            List<ProductDto> components = new ArrayList<>();

            if (response.getBody() != null) {
                Object content = response.getBody().get("content");

                if (content instanceof List<?> items) {
                    components = items.stream()
                            .map(item -> objectMapper.convertValue(
                                    item, ProductDto.class))
                            .toList();
                }
            }

            model.addAttribute("components", components);
            model.addAttribute(
                    "username",
                    session.getAttribute("username")
            );

        } catch (RestClientResponseException e) {

            log.error(
                    "Backend returned {} while loading components: {}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString()
            );

            model.addAttribute("components", List.of());

            if (!model.containsAttribute("error")) {
                model.addAttribute(
                        "error",
                        "Не удалось загрузить комплектующие. Попробуйте позже."
                );
            }

        } catch (Exception e) {

            log.error("Error loading builder components", e);

            model.addAttribute("components", List.of());

            if (!model.containsAttribute("error")) {
                model.addAttribute(
                        "error",
                        "Ошибка загрузки комплектующих."
                );
            }
        }

        return "builder";
    }

    @PostMapping("/builder/save")
    public String saveBuild(
            @RequestParam(required = false) String name,
            @RequestParam(
                    name = "componentId",
                    required = false
            ) List<Long> componentId,
            @RequestParam(
                    name = "quantity",
                    required = false
            ) List<Integer> quantity,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {

        String token = (String) session.getAttribute("token");

        if (token == null || token.isBlank()) {
            return "redirect:/login";
        }

        if (name == null
                || name.isBlank()
                || componentId == null
                || componentId.isEmpty()
                || quantity == null
                || componentId.size() != quantity.size()) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    "Укажите название сборки и выберите комплектующие."
            );

            return "redirect:/builder";
        }

        for (int i = 0; i < componentId.size(); i++) {

            Long id = componentId.get(i);
            Integer qty = quantity.get(i);

            if (id == null
                    || id <= 0
                    || qty == null
                    || qty < 1
                    || qty > 99) {

                redirectAttributes.addFlashAttribute(
                        "error",
                        "Проверьте выбранные комплектующие и их количество."
                );

                return "redirect:/builder";
            }
        }

        try {
            Long userId = extractUserIdFromToken(token);

            if (userId == null) {

                redirectAttributes.addFlashAttribute(
                        "error",
                        "Не удалось определить пользователя. "
                                + "Попробуйте войти в аккаунт заново."
                );

                return "redirect:/builder";
            }

            List<Map<String, Object>> items = new ArrayList<>();

            for (int i = 0; i < componentId.size(); i++) {

                Map<String, Object> item = new LinkedHashMap<>();

                item.put("componentId", componentId.get(i));
                item.put("quantity", quantity.get(i));

                items.add(item);
            }

            Map<String, Object> buildRequest = new LinkedHashMap<>();

            buildRequest.put("userId", userId);
            buildRequest.put("name", name.trim());
            buildRequest.put("items", items);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            restTemplate.exchange(
                    gatewayUrl + "/api/builds",
                    HttpMethod.POST,
                    new HttpEntity<>(buildRequest, headers),
                    Void.class
            );

            log.info(
                    "PC build created successfully for userId={}",
                    userId
            );

            return "redirect:/builds?success";

        } catch (RestClientResponseException e) {

            String responseBody = e.getResponseBodyAsString();

            log.error(
                    "Backend rejected build creation. Status: {}, body: {}",
                    e.getStatusCode(),
                    responseBody
            );

            String message = extractBackendError(responseBody);

            int status = e.getStatusCode().value();

            if (status == 401 || status == 403) {

                message = "Ошибка авторизации. "
                        + "Попробуйте выйти и войти в аккаунт заново.";

            } else if (status == 503) {

                message = "Сервис проверки совместимости "
                        + "временно недоступен. "
                        + "Попробуйте позже.";

            } else if (message == null || message.isBlank()) {

                message = "Не удалось сохранить сборку. "
                        + "Проверьте выбранные комплектующие.";
            }

            redirectAttributes.addFlashAttribute(
                    "error",
                    message
            );

            return "redirect:/builder";

        } catch (Exception e) {

            log.error("Error saving PC build", e);

            redirectAttributes.addFlashAttribute(
                    "error",
                    "Ошибка сохранения сборки. "
                            + "Проверьте работу сервисов Colizant."
            );

            return "redirect:/builder";
        }
    }

    private String extractBackendError(String responseBody) {

        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);

            JsonNode errorNode = root.get("error");

            if (errorNode != null
                    && errorNode.isTextual()
                    && !errorNode.asText().isBlank()) {

                return errorNode.asText();
            }

            JsonNode messageNode = root.get("message");

            if (messageNode != null
                    && messageNode.isTextual()
                    && !messageNode.asText().isBlank()) {

                return messageNode.asText();
            }

            return null;

        } catch (Exception e) {

            log.debug(
                    "Could not parse backend error response",
                    e
            );

            return null;
        }
    }

    private Long extractUserIdFromToken(String token) {

        try {
            String[] parts = token.split("\\.");

            if (parts.length < 2) {
                return null;
            }

            byte[] decoded = java.util.Base64
                    .getUrlDecoder()
                    .decode(parts[1]);

            JsonNode payload = objectMapper.readTree(decoded);
            JsonNode userIdNode = payload.get("userId");

            if (userIdNode == null || userIdNode.isNull()) {
                return null;
            }

            if (userIdNode.canConvertToLong()) {
                return userIdNode.asLong();
            }

            return null;

        } catch (Exception e) {

            log.warn("Cannot extract userId from JWT");

            return null;
        }
    }
}
