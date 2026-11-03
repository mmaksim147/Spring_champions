package org.example.productservice.dto;

public class BuildItemRequest {
    private Long componentId;
    private Integer quantity = 1;

    public Long getComponentId() { return componentId; }
    public void setComponentId(Long componentId) { this.componentId = componentId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}