package org.example.productservice.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "components")
@Schema(description = "Комплектующее ПК")
public class Component {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Название обязательно")
    @Schema(description = "Название компонента", example = "Ryzen 5 7600X")
    private String name;

    @Schema(description = "Производитель", example = "AMD")
    private String manufacturer;

    @NotNull(message = "Цена обязательна")
    @Min(value = 0, message = "Цена не может быть отрицательной")
    @Column(precision = 10, scale = 2)
    @Schema(description = "Цена", example = "25000.00")
    private BigDecimal price;

    @NotNull(message = "Количество обязательно")
    @Min(value = 0, message = "Количество не может быть отрицательным")
    @Schema(description = "Количество на складе", example = "10")
    private Integer stockQuantity = 0;

    @Column(columnDefinition = "TEXT")
    @Schema(description = "Описание")
    private String description;

    @ManyToOne
    @JoinColumn(name = "category_id")
    @Schema(description = "Категория компонента")
    private Category category;

    @Schema(description = "Сокет (для CPU/MOTHERBOARD)", example = "AM5")
    private String socket;

    @Schema(description = "Тип памяти (для RAM/MOTHERBOARD)", example = "DDR5")
    private String ramType;

    @Schema(description = "Форм-фактор (для CASE/MOTHERBOARD)", example = "ATX")
    private String formFactor;

    @Schema(description = "Мощность (для PSU)", example = "750")
    private Integer wattage;

    @Schema(description = "Объём (для STORAGE/RAM)", example = "16GB")
    private String capacity;

    @Column(updatable = false)
    @Schema(description = "Дата создания", accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime createdAt = LocalDateTime.now();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "component_images", joinColumns = @JoinColumn(name = "component_id"))
    @Column(name = "image_url")
    private List<String> imageUrls = new ArrayList<>();

    public Component() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Integer getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(Integer stockQuantity) { this.stockQuantity = stockQuantity; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public String getSocket() { return socket; }
    public void setSocket(String socket) { this.socket = socket; }

    public String getRamType() { return ramType; }
    public void setRamType(String ramType) { this.ramType = ramType; }

    public String getFormFactor() { return formFactor; }
    public void setFormFactor(String formFactor) { this.formFactor = formFactor; }

    public Integer getWattage() { return wattage; }
    public void setWattage(Integer wattage) { this.wattage = wattage; }

    public String getCapacity() { return capacity; }
    public void setCapacity(String capacity) { this.capacity = capacity; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }
}