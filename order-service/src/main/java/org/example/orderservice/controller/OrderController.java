package org.example.orderservice.controller;

import org.example.orderservice.client.ProductClient;
import org.example.orderservice.dto.ComponentDto;
import org.example.orderservice.dto.OrderItemRequest;
import org.example.orderservice.dto.OrderRequest;
import org.example.orderservice.model.Order;
import org.example.orderservice.model.OrderItem;
import org.example.orderservice.repository.OrderRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;

    public OrderController(OrderRepository orderRepository, ProductClient productClient) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
    }

    @GetMapping
    public ResponseEntity<List<Order>> getAll() {
        return ResponseEntity.ok(orderRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody OrderRequest request) {
        Order order = new Order();
        order.setUserId(request.getUserId());

        List<OrderItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (OrderItemRequest itemReq : request.getItems()) {
            ComponentDto component = productClient.getComponentById(itemReq.getComponentId());

            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setComponentId(component.getId());
            item.setQuantity(itemReq.getQuantity());
            item.setPriceAtPurchase(component.getPrice());
            items.add(item);

            total = total.add(component.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity())));

            productClient.decreaseStock(component.getId(), itemReq.getQuantity());
        }

        order.setItems(items);
        order.setTotalPrice(total);

        return ResponseEntity.ok(orderRepository.save(order));
    }
}