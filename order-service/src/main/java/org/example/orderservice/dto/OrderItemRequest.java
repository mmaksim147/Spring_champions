package org.example.orderservice.dto;

public class OrderItemRequest {
    private Long componentId;
    private Integer quantity;

    public Long getComponentId() { return componentId; }
    public void setComponentId(Long componentId) { this.componentId = componentId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}