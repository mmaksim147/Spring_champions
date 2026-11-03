package org.example.compatibilityservice.client;

import org.example.compatibilityservice.dto.ComponentDto;
import org.springframework.stereotype.Component;

@Component
public class ProductClientFallback implements ProductClient {
    @Override
    public ComponentDto getComponentById(Long id) {
        ComponentDto fallback = new ComponentDto();
        fallback.setId(id);
        fallback.setName("Компонент временно недоступен");
        return fallback;
    }
}