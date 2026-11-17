package org.example.frontendservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.example.frontendservice.dto.CartDto;
import org.example.frontendservice.dto.CartItemDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Controller
@RequestMapping("/cart")
public class CartController {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gateway.url}")
    private String gatewayUrl;

    public CartController(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public String showCart(HttpSession session, Model model) {
        String token = getToken(session);

        if (token == null) {
            return "redirect:/login";
        }

        Long userId = getUserId(token);

        if (userId == null) {
            model.addAttribute("cartError", "Не удалось определить пользователя.");
            model.addAttribute("cart", null);
            return "cart";
        }

        HttpEntity<Void> entity = new HttpEntity<>(createHeaders(token));

        try {
            ResponseEntity<CartDto> response = restTemplate.exchange(
                    gatewayUrl + "/api/cart/" + userId,
                    HttpMethod.GET,
                    entity,
                    CartDto.class
            );

            CartDto cart = response.getBody();
            model.addAttribute("cart", cart);

            Map<Long, String> componentNames = new HashMap<>();
            Map<Long, BigDecimal> componentPrices = new HashMap<>();
            Map<Long, BigDecimal> itemTotals = new HashMap<>();

            BigDecimal totalPrice = BigDecimal.ZERO;
            boolean pricesAvailable = true;

            if (cart != null && cart.getItems() != null) {
                for (CartItemDto item : cart.getItems()) {
                    if (item == null || item.getComponentId() == null) {
                        pricesAvailable = false;
                        continue;
                    }

                    Long componentId = item.getComponentId();

                    if (!componentNames.containsKey(componentId)) {
                        try {
                            ResponseEntity<Map> productResponse = restTemplate.exchange(
                                    gatewayUrl + "/api/components/" + componentId,
                                    HttpMethod.GET,
                                    entity,
                                    Map.class
                            );

                            Map<?, ?> product = productResponse.getBody();

                            if (product != null) {
                                Object name = product.get("name");
                                Object price = product.get("price");

                                componentNames.put(
                                        componentId,
                                        name != null
                                                ? name.toString()
                                                : "Комплектующее #" + componentId
                                );

                                if (price != null) {
                                    componentPrices.put(
                                            componentId,
                                            new BigDecimal(price.toString())
                                    );
                                }
                            }
                        } catch (Exception ignored) {
                        }

                        componentNames.putIfAbsent(
                                componentId,
                                "Комплектующее #" + componentId
                        );
                    }

                    BigDecimal price = componentPrices.get(componentId);
                    Integer quantity = item.getQuantity();

                    if (price != null && quantity != null && quantity > 0) {
                        BigDecimal itemTotal = price.multiply(
                                BigDecimal.valueOf(quantity)
                        );

                        itemTotals.put(componentId, itemTotal);
                        totalPrice = totalPrice.add(itemTotal);
                    } else {
                        pricesAvailable = false;
                    }
                }
            }

            model.addAttribute("componentNames", componentNames);
            model.addAttribute("componentPrices", componentPrices);
            model.addAttribute("itemTotals", itemTotals);
            model.addAttribute("totalPrice", totalPrice);
            model.addAttribute("pricesAvailable", pricesAvailable);

        } catch (Exception e) {
            model.addAttribute("cart", null);
            model.addAttribute("cartError", "Не удалось загрузить корзину.");
        }

        return "cart";
    }

    @PostMapping("/add")
    public String addToCart(
            @RequestParam Long componentId,
            @RequestParam(defaultValue = "1") Integer quantity,
            @RequestParam(defaultValue = "catalog") String source,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        String redirect = "component".equals(source)
                && componentId != null && componentId > 0
                ? "redirect:/components/" + componentId
                : "redirect:/catalog";

        String token = getToken(session);

        if (token == null) {
            return "redirect:/login";
        }

        Long userId = getUserId(token);

        if (userId == null) {
            redirectAttributes.addFlashAttribute(
                    "error", "Не удалось определить пользователя."
            );
            return redirect;
        }

        if (componentId == null || componentId <= 0
                || quantity == null || quantity < 1 || quantity > 99) {
            redirectAttributes.addFlashAttribute(
                    "error", "Некорректное количество товара."
            );
            return redirect;
        }

        HttpHeaders headers = createHeaders(token);

        try {
            ResponseEntity<Map> productResponse = restTemplate.exchange(
                    gatewayUrl + "/api/components/" + componentId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );

            if (productResponse.getBody() == null) {
                redirectAttributes.addFlashAttribute(
                        "error", "Комплектующее не найдено."
                );
                return redirect;
            }

            String url = gatewayUrl + "/api/cart/add"
                    + "?userId=" + userId
                    + "&componentId=" + componentId
                    + "&quantity=" + quantity;

            restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    CartDto.class
            );

            redirectAttributes.addFlashAttribute(
                    "success", "Комплектующее добавлено в корзину!"
            );

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(
                    "error", "Не удалось добавить товар в корзину."
            );
        }

        return redirect;
    }

    @PostMapping("/quantity/{componentId}")
    public String updateQuantity(
            @PathVariable Long componentId,
            @RequestParam Integer quantity,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        String token = getToken(session);

        if (token == null) {
            return "redirect:/login";
        }

        Long userId = getUserId(token);

        if (userId == null || componentId == null || componentId <= 0
                || quantity == null || quantity < 1 || quantity > 99) {
            redirectAttributes.addFlashAttribute(
                    "error", "Некорректное количество товара."
            );
            return "redirect:/cart";
        }

        try {
            String url = gatewayUrl
                    + "/api/cart/" + userId
                    + "/items/" + componentId
                    + "?quantity=" + quantity;

            restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    new HttpEntity<>(createHeaders(token)),
                    CartDto.class
            );

            redirectAttributes.addFlashAttribute(
                    "success", "Количество товара изменено."
            );

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(
                    "error", "Не удалось изменить количество товара."
            );
        }

        return "redirect:/cart";
    }

    @PostMapping("/remove/{componentId}")
    public String removeFromCart(
            @PathVariable Long componentId,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        String token = getToken(session);

        if (token == null) {
            return "redirect:/login";
        }

        Long userId = getUserId(token);

        if (userId == null || componentId == null || componentId <= 0) {
            redirectAttributes.addFlashAttribute(
                    "error", "Некорректный идентификатор товара."
            );
            return "redirect:/cart";
        }

        try {
            restTemplate.exchange(
                    gatewayUrl + "/api/cart/" + userId
                            + "/items/" + componentId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(createHeaders(token)),
                    Void.class
            );

            redirectAttributes.addFlashAttribute(
                    "success", "Комплектующее удалено из корзины."
            );
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(
                    "error", "Не удалось удалить комплектующее."
            );
        }

        return "redirect:/cart";
    }

    @PostMapping("/checkout")
    public String checkout(
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        String token = getToken(session);

        if (token == null) {
            return "redirect:/login";
        }

        Long userId = getUserId(token);

        if (userId == null) {
            redirectAttributes.addFlashAttribute(
                    "error", "Не удалось определить пользователя."
            );
            return "redirect:/cart";
        }

        HttpHeaders headers = createHeaders(token);
        CartDto cart;

        try {
            ResponseEntity<CartDto> cartResponse = restTemplate.exchange(
                    gatewayUrl + "/api/cart/" + userId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    CartDto.class
            );

            cart = cartResponse.getBody();

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(
                    "error", "Не удалось получить корзину."
            );
            return "redirect:/cart";
        }

        if (cart == null || cart.getItems() == null
                || cart.getItems().isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "error", "Нельзя оформить пустую корзину."
            );
            return "redirect:/cart";
        }

        List<Map<String, Object>> orderItems = new ArrayList<>();

        for (CartItemDto item : cart.getItems()) {
            if (item == null || item.getComponentId() == null
                    || item.getComponentId() <= 0
                    || item.getQuantity() == null
                    || item.getQuantity() <= 0) {
                redirectAttributes.addFlashAttribute(
                        "error", "В корзине есть некорректная позиция."
                );
                return "redirect:/cart";
            }

            orderItems.add(Map.of(
                    "componentId", item.getComponentId(),
                    "quantity", item.getQuantity()
            ));
        }

        Map<String, Object> orderBody = Map.of(
                "userId", userId,
                "items", orderItems
        );

        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Map> orderResponse = restTemplate.exchange(
                    gatewayUrl + "/api/orders",
                    HttpMethod.POST,
                    new HttpEntity<>(orderBody, headers),
                    Map.class
            );

            if (!orderResponse.getStatusCode().is2xxSuccessful()
                    || orderResponse.getBody() == null
                    || orderResponse.getBody().get("id") == null) {
                redirectAttributes.addFlashAttribute(
                        "error",
                        "Не удалось подтвердить создание заказа. "
                                + "Проверьте раздел заказов."
                );
                return "redirect:/cart";
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Не удалось подтвердить оформление заказа. "
                            + "Проверьте раздел заказов перед повторной попыткой."
            );
            return "redirect:/cart";
        }

        try {
            restTemplate.exchange(
                    gatewayUrl + "/api/cart/" + userId + "/clear",
                    HttpMethod.DELETE,
                    new HttpEntity<>(createHeaders(token)),
                    Void.class
            );

            redirectAttributes.addFlashAttribute(
                    "success", "Заказ успешно оформлен!"
            );
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Заказ создан, но очистить корзину не удалось. "
                            + "Не оформляйте его повторно."
            );
        }

        return "redirect:/orders";
    }

    @PostMapping("/clear")
    public String clearCart(
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        String token = getToken(session);

        if (token == null) {
            return "redirect:/login";
        }

        Long userId = getUserId(token);

        if (userId == null) {
            redirectAttributes.addFlashAttribute(
                    "error", "Не удалось определить пользователя."
            );
            return "redirect:/cart";
        }

        try {
            restTemplate.exchange(
                    gatewayUrl + "/api/cart/" + userId + "/clear",
                    HttpMethod.DELETE,
                    new HttpEntity<>(createHeaders(token)),
                    Void.class
            );

            redirectAttributes.addFlashAttribute(
                    "success", "Корзина очищена."
            );
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(
                    "error", "Не удалось очистить корзину."
            );
        }

        return "redirect:/cart";
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

            if (userIdNode == null || !userIdNode.canConvertToLong()) {
                return null;
            }

            long userId = userIdNode.asLong();
            return userId > 0 ? userId : null;

        } catch (Exception e) {
            return null;
        }
    }
}