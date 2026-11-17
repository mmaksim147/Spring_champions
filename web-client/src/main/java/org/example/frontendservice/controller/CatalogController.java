package org.example.frontendservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.example.frontendservice.dto.ProductDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
public class CatalogController {

    private static final int PAGE_SIZE = 12;
    private static final int MAX_PAGE = 100_000;
    private static final int MAX_SEARCH_LENGTH = 100;
    private static final int MAX_FETCH_PAGES = 100;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gateway.url}")
    private String gatewayUrl;

    public CatalogController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }

    @GetMapping("/catalog")
    public String catalog(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction,
            HttpSession session,
            Model model
    ) {
        Object tokenValue = session.getAttribute("token");

        if (!(tokenValue instanceof String token) || token.isBlank()) {
            return "redirect:/login";
        }

        page = Math.max(0, Math.min(page, MAX_PAGE));

        search = search == null ? "" : search.trim();

        if (search.length() > MAX_SEARCH_LENGTH) {
            search = search.substring(0, MAX_SEARCH_LENGTH);
        }

        if (!List.of("name", "price").contains(sort)) {
            sort = "name";
        }

        if (!List.of("asc", "desc").contains(direction)) {
            direction = "asc";
        }

        model.addAttribute("products", List.of());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", 0);
        model.addAttribute("search", search);
        model.addAttribute("sort", sort);
        model.addAttribute("direction", direction);
        model.addAttribute("username", session.getAttribute("username"));

        Object roles = session.getAttribute("roles");

        model.addAttribute(
                "isAdmin",
                roles instanceof List<?> roleList
                        && roleList.contains("ROLE_ADMIN")
        );

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            if (search.isBlank()) {
                Map<?, ?> body = fetchPage(
                        page, search, sort, direction, entity
                );

                if (body == null) {
                    model.addAttribute(
                            "error",
                            "Сервис каталога вернул пустой ответ."
                    );
                    return "catalog";
                }

                List<ProductDto> products = readProducts(body);

                sortProducts(products, sort, direction);

                int totalPages = readTotalPages(body);

                model.addAttribute("products", products);
                model.addAttribute("totalPages", totalPages);

                if (totalPages > 0 && page >= totalPages) {
                    model.addAttribute(
                            "error",
                            "Запрошенная страница не существует."
                    );
                }

                return "catalog";
            }

            Map<?, ?> firstBody = fetchPage(
                    page, search, sort, direction, entity
            );

            if (firstBody == null) {
                model.addAttribute(
                        "error",
                        "Сервис каталога вернул пустой ответ."
                );
                return "catalog";
            }

            int gatewayPages = readTotalPages(firstBody);

            if (gatewayPages > MAX_FETCH_PAGES) {
                model.addAttribute(
                        "error",
                        "Каталог слишком большой для полного поиска. "
                                + "Попробуйте уточнить запрос."
                );
                return "catalog";
            }

            List<ProductDto> allProducts = new ArrayList<>();

            if (page < gatewayPages || gatewayPages == 0) {
                allProducts.addAll(readProducts(firstBody));
            }

            for (int i = 0; i < gatewayPages; i++) {
                if (i == page) {
                    continue;
                }

                Map<?, ?> body = fetchPage(
                        i, search, sort, direction, entity
                );

                if (body == null) {
                    throw new IllegalStateException(
                            "Пустой ответ gateway на странице " + i
                    );
                }

                allProducts.addAll(readProducts(body));
            }

            String query = search.toLowerCase(Locale.ROOT);

            List<ProductDto> filtered = new ArrayList<>();

            for (ProductDto product : allProducts) {
                String name = safeLower(product.getName());
                String manufacturer = safeLower(product.getManufacturer());

                if (name.contains(query) || manufacturer.contains(query)) {
                    filtered.add(product);
                }
            }

            sortProducts(filtered, sort, direction);

            int totalPages = (filtered.size() + PAGE_SIZE - 1)
                    / PAGE_SIZE;

            model.addAttribute("totalPages", totalPages);

            if (totalPages > 0 && page >= totalPages) {
                model.addAttribute(
                        "error",
                        "Запрошенная страница не существует."
                );
                return "catalog";
            }

            int from = Math.min(page * PAGE_SIZE, filtered.size());
            int to = Math.min(from + PAGE_SIZE, filtered.size());

            model.addAttribute(
                    "products",
                    filtered.subList(from, to)
            );

        } catch (Exception e) {
            model.addAttribute("products", List.of());
            model.addAttribute("totalPages", 0);
            model.addAttribute(
                    "error",
                    "Не удалось загрузить каталог. Попробуйте позже."
            );
            System.err.println("[CATALOG] Ошибка загрузки каталога:");
            e.printStackTrace();
        }

        return "catalog";
    }

    private Map<?, ?> fetchPage(
            int page,
            String search,
            String sort,
            String direction,
            HttpEntity<Void> entity
    ) {
        UriComponentsBuilder builder =
                UriComponentsBuilder.fromHttpUrl(
                                gatewayUrl + "/api/components"
                        )
                        .queryParam("page", page)
                        .queryParam("size", PAGE_SIZE)
                        .queryParam("sort", sort + "," + direction);

        if (!search.isBlank()) {
            builder.queryParam("search", search);
        }

        String url = builder.build().encode().toUriString();

        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                Map.class
        );

        return response.getBody();
    }

    private List<ProductDto> readProducts(Map<?, ?> body) {
        List<ProductDto> products = new ArrayList<>();

        Object contentValue = body.get("content");

        if (contentValue instanceof List<?> content) {
            for (Object item : content) {
                try {
                    products.add(
                            objectMapper.convertValue(
                                    item,
                                    ProductDto.class
                            )
                    );
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        return products;
    }

    private int readTotalPages(Map<?, ?> body) {
        Object value = body.get("totalPages");

        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }

        return 0;
    }

    private String safeLower(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT);
    }

    private void sortProducts(
            List<ProductDto> products,
            String sort,
            String direction
    ) {
        Comparator<ProductDto> comparator;

        if ("price".equals(sort)) {
            comparator = Comparator.comparing(
                    ProductDto::getPrice,
                    Comparator.nullsLast(
                            Comparator.naturalOrder()
                    )
            );
        } else {
            comparator = Comparator.comparing(
                    ProductDto::getName,
                    Comparator.nullsLast(
                            String.CASE_INSENSITIVE_ORDER
                    )
            );
        }

        if ("desc".equals(direction)) {
            comparator = comparator.reversed();
        }

        products.sort(comparator);
    }
}