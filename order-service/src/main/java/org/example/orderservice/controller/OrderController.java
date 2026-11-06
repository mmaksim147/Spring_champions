package org.example.orderservice.controller;

import org.example.orderservice.client.ProductClient;
import org.example.orderservice.dto.ComponentDto;
import org.example.orderservice.dto.OrderItemRequest;
import org.example.orderservice.dto.OrderRequest;
import org.example.orderservice.model.Order;
import org.example.orderservice.model.OrderItem;
import org.example.orderservice.repository.OrderRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;

    public OrderController(
            OrderRepository orderRepository,
            ProductClient productClient
    ) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
    }

    @GetMapping
    public ResponseEntity<List<Order>> getAll() {
        return ResponseEntity.ok(orderRepository.findAll());
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Order>> getByUserId(
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(
                orderRepository.findByUserId(userId)
        );
    }

    @PostMapping
    public ResponseEntity<?> createOrder(
            @RequestBody OrderRequest request
    ) {
        if (request.getUserId() == null
                || request.getUserId() <= 0
                || request.getItems() == null
                || request.getItems().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Некорректные данные заказа")
            );
        }

        Order order = new Order();
        order.setUserId(request.getUserId());

        List<OrderItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        try {
            for (OrderItemRequest itemReq : request.getItems()) {
                if (itemReq.getComponentId() == null
                        || itemReq.getQuantity() == null
                        || itemReq.getQuantity() < 1
                        || itemReq.getQuantity() > 99) {
                    return ResponseEntity.badRequest().body(
                            Map.of("error", "Некорректное количество товара")
                    );
                }

                ComponentDto component = productClient.getComponentById(
                        itemReq.getComponentId()
                );

                if (component == null || component.getPrice() == null) {
                    return ResponseEntity.badRequest().body(
                            Map.of("error", "Комплектующее недоступно")
                    );
                }

                OrderItem item = new OrderItem();
                item.setOrder(order);
                item.setComponentId(component.getId());
                item.setQuantity(itemReq.getQuantity());
                item.setPriceAtPurchase(component.getPrice());

                items.add(item);

                total = total.add(
                        component.getPrice().multiply(
                                BigDecimal.valueOf(itemReq.getQuantity())
                        )
                );

                productClient.decreaseStock(
                        component.getId(),
                        itemReq.getQuantity()
                );
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Ошибка оформления: " + e.getMessage())
            );
        }

        order.setItems(items);
        order.setTotalPrice(total);

        return ResponseEntity.ok(
                orderRepository.save(order)
        );
    }

    @PatchMapping("/{orderId}/items/{itemId}")
    @Transactional
    public ResponseEntity<?> updateItemQuantity(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @RequestParam Long userId,
            @RequestParam Integer quantity
    ) {
        if (quantity == null || quantity < 1 || quantity > 99) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Количество должно быть от 1 до 99")
            );
        }

        Optional<Order> optionalOrder =
                orderRepository.findById(orderId);

        if (optionalOrder.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Order order = optionalOrder.get();

        if (!userId.equals(order.getUserId())) {
            return ResponseEntity.status(403).body(
                    Map.of("error", "Нет доступа к заказу")
            );
        }

        if (!"NEW".equals(order.getStatus())) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "error",
                            "Изменять можно только новые заказы"
                    )
            );
        }

        if (order.getItems() == null) {
            return ResponseEntity.notFound().build();
        }

        Optional<OrderItem> optionalItem = order.getItems().stream()
                .filter(item -> itemId.equals(item.getId()))
                .findFirst();

        if (optionalItem.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        OrderItem item = optionalItem.get();

        int oldQuantity = item.getQuantity();
        int difference = quantity - oldQuantity;

        if (difference == 0) {
            return ResponseEntity.ok(order);
        }

        try {
            if (difference > 0) {
                productClient.decreaseStock(
                        item.getComponentId(),
                        difference
                );
            } else {
                productClient.increaseStock(
                        item.getComponentId(),
                        -difference
                );
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "error",
                            "Не удалось изменить остаток товара: "
                                    + e.getMessage()
                    )
            );
        }

        item.setQuantity(quantity);

        BigDecimal total = BigDecimal.ZERO;

        for (OrderItem orderItem : order.getItems()) {
            total = total.add(
                    orderItem.getPriceAtPurchase().multiply(
                            BigDecimal.valueOf(orderItem.getQuantity())
                    )
            );
        }

        order.setTotalPrice(total);

        try {
            return ResponseEntity.ok(
                    orderRepository.saveAndFlush(order)
            );
        } catch (Exception e) {
            try {
                if (difference > 0) {
                    productClient.increaseStock(
                            item.getComponentId(),
                            difference
                    );
                } else {
                    productClient.decreaseStock(
                            item.getComponentId(),
                            -difference
                    );
                }
            } catch (Exception ignored) {
            }

            throw e;
        }
    }
}