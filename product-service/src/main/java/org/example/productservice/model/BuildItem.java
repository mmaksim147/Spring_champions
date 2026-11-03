package org.example.productservice.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;

@Entity
@Table(name = "build_items")
@Schema(description = "Элемент сборки — компонент + количество")
public class BuildItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "build_id", nullable = false)
    @JsonIgnore
    private Build build;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "component_id", nullable = false)
    private Component component;

    @Schema(description = "Количество", example = "1")
    private Integer quantity = 1;

    public BuildItem() {}

    public BuildItem(Build build, Component component, Integer quantity) {
        this.build = build;
        this.component = component;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Build getBuild() { return build; }
    public void setBuild(Build build) { this.build = build; }

    public Component getComponent() { return component; }
    public void setComponent(Component component) { this.component = component; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}