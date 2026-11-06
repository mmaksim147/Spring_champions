package org.example.orderservice.client;

import org.example.orderservice.dto.ComponentDto;
import org.springframework.stereotype.Component;

@Component
public class ProductClientFallback implements ProductClient {

    @Override
    public ComponentDto getComponentById(Long id) {
        throw new RuntimeException("Товар не найден или сервис товаров недоступен");
    }

    @Override
    public void decreaseStock(Long id, Integer quantity) {
        throw new RuntimeException("Недостаточно товара на складе или сервис товаров недоступен");
    }

    @Override
    public void increaseStock(Long id, Integer quantity) {
        throw new RuntimeException("Не удалось вернуть товар на склад: сервис товаров недоступен");
    }
}