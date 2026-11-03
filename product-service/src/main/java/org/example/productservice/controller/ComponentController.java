package org.example.productservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.productservice.model.Component;
import org.example.productservice.repository.CategoryRepository;
import org.example.productservice.repository.ComponentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/components")
@Tag(name = "Компоненты", description = "API для управления компонентами ПК")
public class ComponentController {

    private final ComponentRepository componentRepository;
    private final CategoryRepository categoryRepository;

    public ComponentController(ComponentRepository componentRepository,
                               CategoryRepository categoryRepository) {
        this.componentRepository = componentRepository;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    @Operation(summary = "Получить компоненты с пагинацией и поиском")
    public ResponseEntity<Page<Component>> getAll(
            @Parameter(description = "Номер страницы (с 0)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Размер страницы")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Поиск по названию (частичное совпадение)")
            @RequestParam(required = false) String search) {

        Pageable pageable = PageRequest.of(page, size);
        Page<Component> result = (search != null && !search.isBlank())
                ? componentRepository.findByNameContainingIgnoreCase(search, pageable)
                : componentRepository.findAll(pageable);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить компонент по ID")
    public ResponseEntity<Component> getById(@PathVariable Long id) {
        return componentRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-category/{categoryId}")
    @Operation(summary = "Получить компоненты по категории (для Feign)")
    public ResponseEntity<List<Component>> getByCategory(@PathVariable Long categoryId) {
        return ResponseEntity.ok(componentRepository.findByCategoryId(categoryId));
    }

    @PostMapping
    @Operation(summary = "Создать компонент")
    public ResponseEntity<Component> create(@Valid @RequestBody Component component) {
        if (component.getCategory() != null && component.getCategory().getId() != null) {
            component.setCategory(
                    categoryRepository.findById(component.getCategory().getId()).orElse(null)
            );
        }
        return ResponseEntity.status(201).body(componentRepository.save(component));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Обновить компонент")
    public ResponseEntity<Component> update(@PathVariable Long id,
                                            @Valid @RequestBody Component component) {
        return componentRepository.findById(id)
                .map(existing -> {
                    existing.setName(component.getName());
                    existing.setManufacturer(component.getManufacturer());
                    existing.setPrice(component.getPrice());
                    existing.setStockQuantity(component.getStockQuantity());
                    existing.setDescription(component.getDescription());
                    existing.setSocket(component.getSocket());
                    existing.setRamType(component.getRamType());
                    existing.setFormFactor(component.getFormFactor());
                    existing.setWattage(component.getWattage());
                    existing.setCapacity(component.getCapacity());
                    if (component.getCategory() != null && component.getCategory().getId() != null) {
                        existing.setCategory(
                                categoryRepository.findById(component.getCategory().getId()).orElse(null)
                        );
                    }
                    return ResponseEntity.ok(componentRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить компонент")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        componentRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/decrease-stock")
    @Operation(summary = "Уменьшить остаток (для order-service)")
    public ResponseEntity<Component> decreaseStock(@PathVariable Long id,
                                                   @RequestParam Integer quantity) {
        return componentRepository.findById(id)
                .map(existing -> {
                    if (existing.getStockQuantity() < quantity) {
                        return ResponseEntity.badRequest().<Component>build();
                    }
                    existing.setStockQuantity(existing.getStockQuantity() - quantity);
                    return ResponseEntity.ok(componentRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}