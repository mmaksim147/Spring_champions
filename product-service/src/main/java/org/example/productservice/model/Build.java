package org.example.productservice.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "builds")
@Schema(description = "Сборка ПК пользователя")
public class Build {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Schema(description = "ID пользователя (из auth-service)", example = "1")
    private Long userId;

    @Schema(description = "Название сборки", example = "Мой игровой ПК")
    private String name;

    @Column(precision = 10, scale = 2)
    @Schema(description = "Общая стоимость", accessMode = Schema.AccessMode.READ_ONLY)
    private BigDecimal totalPrice = BigDecimal.ZERO;

    @Column(updatable = false)
    @Schema(description = "Дата создания", accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "build", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<BuildItem> items = new ArrayList<>();

    public Build() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<BuildItem> getItems() { return items; }
    public void setItems(List<BuildItem> items) { this.items = items; }
}