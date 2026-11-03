package org.example.compatibilityservice.controller;

import org.example.compatibilityservice.model.CompatibilityCheckLog;
import org.example.compatibilityservice.repository.CompatibilityCheckLogRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/logs")
public class LogController {

    private final CompatibilityCheckLogRepository repository;

    public LogController(CompatibilityCheckLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<List<CompatibilityCheckLog>> getAllLogs() {
        return ResponseEntity.ok(repository.findAll());
    }
}