package org.example.productservice.dto;

import java.util.List;

public class BuildRequest {
    private Long userId;
    private String name;
    private List<BuildItemRequest> items;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<BuildItemRequest> getItems() { return items; }
    public void setItems(List<BuildItemRequest> items) { this.items = items; }
}