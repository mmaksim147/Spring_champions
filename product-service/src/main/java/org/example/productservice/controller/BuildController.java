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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/builds")
@Tag(name = "Сборки", description = "API для сборок ПК")
public class BuildController {

    private final BuildRepository buildRepository;
    private final ComponentRepository componentRepository;

    public BuildController(BuildRepository buildRepository,
                           ComponentRepository componentRepository) {
        this.buildRepository = buildRepository;
        this.componentRepository = componentRepository;
    }

    @GetMapping
    @Operation(summary = "Все сборки или по userId")
    public ResponseEntity<List<Build>> getAll(@RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(userId != null
                ? buildRepository.findByUserId(userId)
                : buildRepository.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Сборка по ID")
    public ResponseEntity<Build> getById(@PathVariable Long id) {
        return buildRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Создать сборку (с пересчётом totalPrice)")
    public ResponseEntity<Build> create(@RequestBody BuildRequest request) {
        Build build = new Build();
        build.setUserId(request.getUserId());
        build.setName(request.getName());

        List<BuildItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        if (request.getItems() != null) {
            for (BuildItemRequest itemReq : request.getItems()) {
                Component component = componentRepository.findById(itemReq.getComponentId())
                        .orElseThrow(() -> new RuntimeException("Компонент не найден: " + itemReq.getComponentId()));
                int qty = itemReq.getQuantity() != null ? itemReq.getQuantity() : 1;
                BuildItem item = new BuildItem(build, component, qty);
                items.add(item);
                total = total.add(component.getPrice().multiply(BigDecimal.valueOf(qty)));
            }
        }

        build.setItems(items);
        build.setTotalPrice(total);

        return ResponseEntity.status(201).body(buildRepository.save(build));
    }

    @PostMapping("/{id}/items")
    @Operation(summary = "Добавить компонент в сборку + пересчёт")
    public ResponseEntity<Build> addItem(@PathVariable Long id, @RequestBody BuildItemRequest itemReq) {
        return buildRepository.findById(id).map(build -> {
            Component component = componentRepository.findById(itemReq.getComponentId())
                    .orElseThrow(() -> new RuntimeException("Компонент не найден"));
            int qty = itemReq.getQuantity() != null ? itemReq.getQuantity() : 1;
            build.getItems().add(new BuildItem(build, component, qty));
            recalcTotal(build);
            return ResponseEntity.ok(buildRepository.save(build));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @Operation(summary = "Удалить компонент из сборки + пересчёт")
    public ResponseEntity<Build> removeItem(@PathVariable Long id, @PathVariable Long itemId) {
        return buildRepository.findById(id).map(build -> {
            build.getItems().removeIf(i -> i.getId().equals(itemId));
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

    private void recalcTotal(Build build) {
        BigDecimal total = build.getItems().stream()
                .map(i -> i.getComponent().getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        build.setTotalPrice(total);
    }
}