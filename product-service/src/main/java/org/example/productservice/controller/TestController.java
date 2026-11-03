package org.example.productservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/products")
public class TestController {

    @GetMapping("/test-headers")
    public ResponseEntity<Map<String, String>> testHeaders(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Name", required = false) String username,
            @RequestHeader(value = "X-User-Roles", required = false) String roles
    ) {
        return ResponseEntity.ok(Map.of(
                "X-User-Id", userId != null ? userId : "null",
                "X-User-Name", username != null ? username : "null",
                "X-User-Roles", roles != null ? roles : "null"
        ));
    }
}