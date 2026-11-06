
package org.example.productservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.productservice.model.Component;
import org.example.productservice.repository.BuildItemRepository;
import org.example.productservice.repository.CategoryRepository;
import org.example.productservice.repository.ComponentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/components")
@Tag(name = "Компоненты", description = "API для управления компонентами ПК")
public class ComponentController {

    private final ComponentRepository componentRepository;
    private final CategoryRepository categoryRepository;
    private final BuildItemRepository buildItemRepository;
    private final TransactionTemplate transactionTemplate;

    public ComponentController(
            ComponentRepository componentRepository,
            CategoryRepository categoryRepository,
            BuildItemRepository buildItemRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.componentRepository = componentRepository;
        this.categoryRepository = categoryRepository;
        this.buildItemRepository = buildItemRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @GetMapping
    @Operation(summary = "Получить компоненты с пагинацией и поиском")
    public ResponseEntity<Page<Component>> getAll(
            @Parameter(description = "Номер страницы с 0")
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search
    ) {
        Pageable pageable = PageRequest.of(page, size);

        Page<Component> result =
                search != null && !search.isBlank()
                        ? componentRepository.findByNameContainingIgnoreCase(
                        search,
                        pageable
                )
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
    public ResponseEntity<List<Component>> getByCategory(
            @PathVariable Long categoryId
    ) {
        return ResponseEntity.ok(
                componentRepository.findByCategoryId(categoryId)
        );
    }

    @PostMapping
    public ResponseEntity<Component> create(
            @Valid @RequestBody Component component
    ) {
        if (component.getCategory() != null
                && component.getCategory().getId() != null) {

            component.setCategory(
                    categoryRepository.findById(
                            component.getCategory().getId()
                    ).orElse(null)
            );
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(componentRepository.save(component));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить компонент, если он не используется")
    public ResponseEntity<?> delete(@PathVariable Long id) {

        if (id == null || id <= 0) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "error",
                            "Некорректный ID компонента."
                    )
            );
        }

        try {
            Boolean deleted = transactionTemplate.execute(status -> {

                if (!componentRepository.existsById(id)) {
                    return false;
                }

                if (buildItemRepository.existsByComponentId(id)) {
                    throw new ComponentInUseException();
                }

                componentRepository.deleteById(id);
                componentRepository.flush();

                return true;
            });

            if (Boolean.FALSE.equals(deleted)) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.noContent().build();

        } catch (ComponentInUseException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    Map.of(
                            "error",
                            "Нельзя удалить компонент: он используется в сохранённых сборках."
                    )
            );

        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    Map.of(
                            "error",
                            "Нельзя удалить компонент: на него ссылаются другие данные."
                    )
            );
        }
    }

    private static class ComponentInUseException extends RuntimeException {
    }

    @PutMapping("/{id}")
    public ResponseEntity<Component> update(
            @PathVariable Long id,
            @Valid @RequestBody Component component
    ) {
        return componentRepository.findById(id).map(existing -> {

            existing.setName(component.getName());
            existing.setPrice(component.getPrice());
            existing.setStockQuantity(component.getStockQuantity());
            existing.setManufacturer(component.getManufacturer());

            return ResponseEntity.ok(
                    componentRepository.save(existing)
            );

        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/decrease-stock")
    @Transactional
    @Operation(summary = "Уменьшить остаток")
    public ResponseEntity<Component> decreaseStock(
            @PathVariable Long id,
            @RequestParam Integer quantity
    ) {
        if (quantity == null || quantity <= 0) {
            return ResponseEntity.badRequest().build();
        }

        return componentRepository.findById(id)
                .map(existing -> {

                    if (existing.getStockQuantity() == null
                            || existing.getStockQuantity() < quantity) {
                        return ResponseEntity.badRequest()
                                .<Component>build();
                    }

                    existing.setStockQuantity(
                            existing.getStockQuantity() - quantity
                    );

                    return ResponseEntity.ok(
                            componentRepository.save(existing)
                    );

                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/increase-stock")
    @Transactional
    @Operation(summary = "Вернуть комплектующие на склад")
    public ResponseEntity<Component> increaseStock(
            @PathVariable Long id,
            @RequestParam Integer quantity
    ) {
        if (quantity == null || quantity <= 0) {
            return ResponseEntity.badRequest().build();
        }

        return componentRepository.findById(id)
                .map(existing -> {

                    if (existing.getStockQuantity() == null) {
                        return ResponseEntity.badRequest()
                                .<Component>build();
                    }

                    existing.setStockQuantity(
                            existing.getStockQuantity() + quantity
                    );

                    return ResponseEntity.ok(
                            componentRepository.save(existing)
                    );

                })
                .orElse(ResponseEntity.notFound().build());
    }
}
