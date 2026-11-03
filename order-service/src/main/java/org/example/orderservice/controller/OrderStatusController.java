package org.example.orderservice.controller;

import org.example.orderservice.model.Order;
import org.example.orderservice.repository.OrderRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/status")
public class OrderStatusController {

    private final OrderRepository repository;

    public OrderStatusController(OrderRepository repository) {
        this.repository = repository;
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Order> updateStatus(@PathVariable Long id, @RequestParam String status) {
        return repository.findById(id)
                .map(order -> {
                    order.setStatus(status);
                    return ResponseEntity.ok(repository.save(order));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}