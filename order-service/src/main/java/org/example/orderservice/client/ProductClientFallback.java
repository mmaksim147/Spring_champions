package org.example.orderservice.client;

import org.example.orderservice.dto.ComponentDto;
import org.springframework.stereotype.Component;

@Component
public class ProductClientFallback implements ProductClient {
    @Override
    public ComponentDto getComponentById(Long id) {
        throw new RuntimeException("Сервис продуктов недоступен. Невозможно создать заказ.");
    }

    @Override
    public void decreaseStock(Long id, Integer quantity) {
        System.out.println("Не удалось списать товар со склада. Сервис недоступен.");
    }
}