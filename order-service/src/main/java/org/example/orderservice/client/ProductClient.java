package org.example.orderservice.client;

import org.example.orderservice.dto.ComponentDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "product-service", fallback = ProductClientFallback.class)
public interface ProductClient {
    @GetMapping("/components/{id}")
    ComponentDto getComponentById(@PathVariable("id") Long id);

    @PatchMapping("/components/{id}/decrease-stock")
    void decreaseStock(@PathVariable("id") Long id, @RequestParam("quantity") Integer quantity);
}