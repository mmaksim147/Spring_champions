/*package org.example.productservice.controller;

import org.example.productservice.dto.ProductDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {

    @GetMapping
    public ResponseEntity<List<ProductDto>> getAll() {
        return ResponseEntity.ok(List.of(
                new ProductDto(1L, "Product 1", 1000.0),
                new ProductDto(2L, "Product 2", 2000.0)
        ));
    }
}*/