package org.example.apigateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/auth")
    public Mono<ResponseEntity<Map<String, String>>> authFallback() {
        return fallback("Auth service");
    }

    @GetMapping("/product")
    public Mono<ResponseEntity<Map<String, String>>> productFallback() {
        return fallback("Product service");
    }

    @GetMapping("/compatibility")
    public Mono<ResponseEntity<Map<String, String>>> compatibilityFallback() {
        return fallback("Compatibility service");
    }

    @GetMapping("/order")
    public Mono<ResponseEntity<Map<String, String>>> orderFallback() {
        return fallback("Order service");
    }

    private Mono<ResponseEntity<Map<String, String>>> fallback(String serviceName) {
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", serviceName + " временно недоступен",
                        "status", "503",
                        "timestamp", String.valueOf(System.currentTimeMillis())
                )));
    }
}