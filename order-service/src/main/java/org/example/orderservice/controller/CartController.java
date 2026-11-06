package org.example.orderservice.controller;

import org.example.orderservice.model.Cart;
import org.example.orderservice.model.CartItem;
import org.example.orderservice.repository.CartRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/cart")
public class CartController {

    private final CartRepository cartRepository;

    public CartController(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<Cart> getCart(@PathVariable Long userId) {
        Cart cart = cartRepository.findByUserId(userId).orElseGet(() -> {
            Cart newCart = new Cart();
            newCart.setUserId(userId);
            return cartRepository.save(newCart);
        });

        return ResponseEntity.ok(cart);
    }

    @PostMapping("/add")
    @Transactional
    public ResponseEntity<Cart> addToCart(
            @RequestParam Long userId,
            @RequestParam Long componentId,
            @RequestParam Integer quantity
    ) {
        if (userId == null || userId <= 0
                || componentId == null || componentId <= 0
                || quantity == null || quantity < 1 || quantity > 99) {
            return ResponseEntity.badRequest().build();
        }

        Cart cart = cartRepository.findByUserId(userId).orElseGet(() -> {
            Cart newCart = new Cart();
            newCart.setUserId(userId);
            return cartRepository.save(newCart);
        });

        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(i -> componentId.equals(i.getComponentId()))
                .findFirst();

        if (existingItem.isPresent()) {
            int newQuantity = existingItem.get().getQuantity() + quantity;

            if (newQuantity > 99) {
                return ResponseEntity.badRequest().build();
            }

            existingItem.get().setQuantity(newQuantity);
        } else {
            CartItem newItem = new CartItem();
            newItem.setCart(cart);
            newItem.setComponentId(componentId);
            newItem.setQuantity(quantity);
            cart.getItems().add(newItem);
        }

        return ResponseEntity.ok(cartRepository.save(cart));
    }

    @PatchMapping("/{userId}/items/{componentId}")
    @Transactional
    public ResponseEntity<Cart> updateQuantity(
            @PathVariable Long userId,
            @PathVariable Long componentId,
            @RequestParam Integer quantity
    ) {
        if (quantity == null || quantity < 1 || quantity > 99) {
            return ResponseEntity.badRequest().build();
        }

        Optional<Cart> optionalCart = cartRepository.findByUserId(userId);

        if (optionalCart.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Cart cart = optionalCart.get();

        Optional<CartItem> optionalItem = cart.getItems().stream()
                .filter(item -> componentId.equals(item.getComponentId()))
                .findFirst();

        if (optionalItem.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        optionalItem.get().setQuantity(quantity);

        return ResponseEntity.ok(cartRepository.save(cart));
    }

    @DeleteMapping("/{userId}/items/{componentId}")
    @Transactional
    public ResponseEntity<Void> removeItem(
            @PathVariable Long userId,
            @PathVariable Long componentId
    ) {
        Optional<Cart> optionalCart = cartRepository.findByUserId(userId);

        if (optionalCart.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Cart cart = optionalCart.get();

        boolean removed = cart.getItems().removeIf(
                item -> componentId.equals(item.getComponentId())
        );

        if (!removed) {
            return ResponseEntity.notFound().build();
        }

        cartRepository.save(cart);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}/clear")
    @Transactional
    public ResponseEntity<Void> clearCart(@PathVariable Long userId) {
        cartRepository.findByUserId(userId).ifPresent(cart -> {
            cart.getItems().clear();
            cartRepository.save(cart);
        });

        return ResponseEntity.noContent().build();
    }
}