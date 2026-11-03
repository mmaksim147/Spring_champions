package org.example.orderservice.controller;

import org.example.orderservice.model.OrderItem;
import org.example.orderservice.repository.OrderItemRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/order-items")
public class OrderItemController {

    private final OrderItemRepository repository;

    public OrderItemController(OrderItemRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<OrderItem>> getByOrderId(@PathVariable Long orderId) {
        return ResponseEntity.ok(repository.findByOrderId(orderId));
    }
}