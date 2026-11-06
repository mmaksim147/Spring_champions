
package org.example.productservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.productservice.dto.BuildItemRequest;
import org.example.productservice.dto.BuildRequest;
import org.example.productservice.model.Build;
import org.example.productservice.model.BuildItem;
import org.example.productservice.model.Component;
import org.example.productservice.repository.BuildRepository;
import org.example.productservice.repository.ComponentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/builds")
@Tag(name = "Сборки", description = "API для сборок ПК")
public class BuildController {

    private final BuildRepository buildRepository;
    private final ComponentRepository componentRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${compatibility.rules-url:http://localhost:8080/api/compatibility/rules}")
    private String rulesUrl;

    public BuildController(
            BuildRepository buildRepository,
            ComponentRepository componentRepository
    ) {
        this.buildRepository = buildRepository;
        this.componentRepository = componentRepository;
    }

    @GetMapping
    @Operation(summary = "Все сборки или по userId")
    public ResponseEntity<List<Build>> getAll(
            @RequestParam(required = false) Long userId
    ) {
        return ResponseEntity.ok(
                userId != null
                        ? buildRepository.findByUserId(userId)
                        : buildRepository.findAll()
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Сборка по ID")
    public ResponseEntity<Build> getById(@PathVariable Long id) {
        return buildRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Создать сборку с проверкой совместимости")
    public ResponseEntity<?> create(
            @RequestBody BuildRequest request,
            @RequestHeader(
                    value = HttpHeaders.AUTHORIZATION,
                    required = false
            ) String authorization
    ) {
        if (request == null
                || request.getUserId() == null
                || request.getName() == null
                || request.getName().isBlank()
                || request.getItems() == null
                || request.getItems().isEmpty()) {

            return badRequest("Заполните название и комплектующие сборки.");
        }

        try {
            Build build = new Build();
            build.setUserId(request.getUserId());
            build.setName(request.getName().trim());

            List<BuildItem> items = new ArrayList<>();

            for (BuildItemRequest itemReq : request.getItems()) {
                if (itemReq == null) {
                    return badRequest("Некорректная позиция сборки.");
                }

                Long componentId = itemReq.getComponentId();
                Integer quantity = itemReq.getQuantity();

                if (componentId == null
                        || componentId <= 0
                        || quantity == null
                        || quantity < 1
                        || quantity > 99) {

                    return badRequest(
                            "Некорректный ID или количество комплектующего."
                    );
                }

                Component component = componentRepository
                        .findById(componentId)
                        .orElse(null);

                if (component == null) {
                    return badRequest(
                            "Комплектующее не найдено: " + componentId
                    );
                }

                items.add(new BuildItem(build, component, quantity));
            }

            String error = validateBuild(items, authorization);

            if (error != null) {
                return badRequest(error);
            }

            build.setItems(items);
            recalcTotal(build);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(buildRepository.save(build));

        } catch (RuleServiceException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error",
                            "Не удалось сохранить сборку."
                    ));
        }
    }

    @PostMapping("/{id}/items")
    @Operation(summary = "Добавить совместимый компонент в сборку")
    public ResponseEntity<?> addItem(
            @PathVariable Long id,
            @RequestBody BuildItemRequest itemReq,
            @RequestHeader(
                    value = HttpHeaders.AUTHORIZATION,
                    required = false
            ) String authorization
    ) {
        Build build = buildRepository.findById(id).orElse(null);

        if (build == null) {
            return ResponseEntity.notFound().build();
        }

        if (itemReq == null
                || itemReq.getComponentId() == null
                || itemReq.getComponentId() <= 0
                || itemReq.getQuantity() == null
                || itemReq.getQuantity() < 1
                || itemReq.getQuantity() > 99) {

            return badRequest("Некорректные данные комплектующего.");
        }

        Component component = componentRepository
                .findById(itemReq.getComponentId())
                .orElse(null);

        if (component == null) {
            return badRequest("Комплектующее не найдено.");
        }

        try {
            // Проверяем предполагаемый состав,
            // не изменяя сохранённую сборку заранее.
            List<BuildItem> proposedItems =
                    new ArrayList<>(build.getItems());

            proposedItems.add(
                    new BuildItem(build, component, itemReq.getQuantity())
            );

            String error = validateBuild(proposedItems, authorization);

            if (error != null) {
                return badRequest(error);
            }

            build.getItems().add(
                    new BuildItem(build, component, itemReq.getQuantity())
            );

            recalcTotal(build);

            return ResponseEntity.ok(buildRepository.save(build));

        } catch (RuleServiceException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @Operation(summary = "Удалить компонент из сборки + пересчёт")
    public ResponseEntity<Build> removeItem(
            @PathVariable Long id,
            @PathVariable Long itemId
    ) {
        return buildRepository.findById(id).map(build -> {
            build.getItems().removeIf(
                    item -> Objects.equals(item.getId(), itemId)
            );

            recalcTotal(build);

            return ResponseEntity.ok(buildRepository.save(build));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить сборку")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        buildRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private String validateBuild(
            List<BuildItem> items,
            String authorization
    ) {
        if (items.isEmpty()) {
            return "Сборка не может быть пустой.";
        }

        Map<String, List<BuildItem>> byCategory =
                new HashMap<>();

        Set<Long> componentIds = new HashSet<>();

        for (BuildItem item : items) {
            Component component = item.getComponent();

            if (component == null
                    || component.getId() == null
                    || component.getCategory() == null
                    || component.getCategory().getName() == null) {

                return "У одного из комплектующих не указана категория.";
            }

            if (!componentIds.add(component.getId())) {
                return "Комплектующее «" + component.getName()
                        + "» уже добавлено в сборку. "
                        + "Укажите нужное количество одной позицией.";
            }

            String category = component.getCategory()
                    .getName()
                    .trim()
                    .toUpperCase(Locale.ROOT);

            byCategory.computeIfAbsent(
                    category,
                    key -> new ArrayList<>()
            ).add(item);
        }

        for (String category : List.of(
                "CPU", "MOTHERBOARD", "CASE", "PSU"
        )) {
            List<BuildItem> categoryItems =
                    byCategory.getOrDefault(category, List.of());

            if (categoryItems.size() > 1) {
                return "Нельзя добавить несколько комплектующих "
                        + "категории " + category + ".";
            }

            if (!categoryItems.isEmpty()
                    && categoryItems.get(0).getQuantity() != 1) {

                return "Для категории " + category
                        + " количество должно быть равно 1.";
            }
        }

        List<RuleDto> rules = loadRules(authorization);

        if (rules.isEmpty()) {
            throw new RuleServiceException(
                    "Правила совместимости отсутствуют. "
                            + "Сохранение сборки временно недоступно."
            );
        }

        // Проверяем все подходящие пары по правилам из БД.
        for (RuleDto rule : rules) {
            if (rule.sourceCategory == null
                    || rule.targetCategory == null
                    || rule.matchingField == null) {

                throw new RuleServiceException(
                        "В базе обнаружено некорректное правило совместимости."
                );
            }

            String sourceCategory =
                    rule.sourceCategory.trim().toUpperCase(Locale.ROOT);

            String targetCategory =
                    rule.targetCategory.trim().toUpperCase(Locale.ROOT);

            List<BuildItem> sources =
                    byCategory.getOrDefault(sourceCategory, List.of());

            List<BuildItem> targets =
                    byCategory.getOrDefault(targetCategory, List.of());

            for (BuildItem sourceItem : sources) {
                for (BuildItem targetItem : targets) {

                    Component source = sourceItem.getComponent();
                    Component target = targetItem.getComponent();

                    if (!matches(
                            source,
                            target,
                            rule.matchingField
                    )) {
                        String description =
                                rule.description != null
                                        && !rule.description.isBlank()
                                        ? rule.description
                                        : "Несовместимые комплектующие";

                        return description
                                + ": «" + source.getName()
                                + "» и «" + target.getName() + "». "
                                + "Проверьте " + rule.matchingField + ".";
                    }
                }
            }
        }

        return null;
    }

    private boolean matches(
            Component source,
            Component target,
            String field
    ) {
        return switch (field.trim()) {
            case "socket" -> sameValue(
                    source.getSocket(),
                    target.getSocket()
            );

            case "ramType" -> sameValue(
                    source.getRamType(),
                    target.getRamType()
            );

            case "formFactor" -> compatibleFormFactors(
                    source.getFormFactor(),
                    target.getFormFactor()
            );

            default -> throw new RuleServiceException(
                    "Неизвестное правило совместимости: " + field
            );
        };
    }

    private boolean sameValue(String first, String second) {
        return first != null
                && second != null
                && !first.isBlank()
                && !second.isBlank()
                && first.trim().equalsIgnoreCase(second.trim());
    }

    private boolean compatibleFormFactors(
            String motherboardFormFactor,
            String caseFormFactor
    ) {
        if (motherboardFormFactor == null
                || caseFormFactor == null) {
            return false;
        }

        String board = normalizeFormFactor(motherboardFormFactor);
        String pcCase = normalizeFormFactor(caseFormFactor);

        if (board.isBlank() || pcCase.isBlank()) {
            return false;
        }

        if (board.equals(pcCase)) {
            return true;
        }

        // Базовая поддержка распространённых размеров.
        return switch (pcCase) {
            case "EATX" -> Set.of(
                    "ATX", "MATX", "MITX"
            ).contains(board);

            case "ATX" -> Set.of(
                    "MATX", "MITX"
            ).contains(board);

            case "MATX" -> "MITX".equals(board);

            default -> false;
        };
    }

    private String normalizeFormFactor(String value) {
        return value.trim()
                .toUpperCase(Locale.ROOT)
                .replace("-", "")
                .replace("_", "")
                .replace(" ", "")
                .replace("MICROATX", "MATX")
                .replace("MINIITX", "MITX")
                .replace("EXTENDEDATX", "EATX");
    }

    private List<RuleDto> loadRules(String authorization) {
        try {
            HttpHeaders headers = new HttpHeaders();

            if (authorization != null
                    && !authorization.isBlank()) {
                headers.set(
                        HttpHeaders.AUTHORIZATION,
                        authorization
                );
            }

            ResponseEntity<RuleDto[]> response =
                    restTemplate.exchange(
                            rulesUrl,
                            HttpMethod.GET,
                            new HttpEntity<>(headers),
                            RuleDto[].class
                    );

            RuleDto[] body = response.getBody();

            if (body == null) {
                throw new RuleServiceException(
                        "Сервис вернул пустой ответ с правилами."
                );
            }

            return Arrays.asList(body);

        } catch (RuleServiceException e) {
            throw e;

        } catch (Exception e) {
            throw new RuleServiceException(
                    "Не удалось загрузить правила совместимости."
            );
        }
    }

    private ResponseEntity<Map<String, String>> badRequest(
            String message
    ) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", message));
    }

    private void recalcTotal(Build build) {
        BigDecimal total = build.getItems().stream()
                .map(item ->
                        item.getComponent()
                                .getPrice()
                                .multiply(
                                        BigDecimal.valueOf(
                                                item.getQuantity()
                                        )
                                )
                )
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        build.setTotalPrice(total);
    }

    // DTO ответа GET /rules.
    // Поля public нужны для простой десериализации Jackson.
    public static class RuleDto {
        public String sourceCategory;
        public String targetCategory;
        public String matchingField;
        public String description;

        public RuleDto() {
        }
    }

    private static class RuleServiceException
            extends RuntimeException {

        RuleServiceException(String message) {
            super(message);
        }
    }
}
