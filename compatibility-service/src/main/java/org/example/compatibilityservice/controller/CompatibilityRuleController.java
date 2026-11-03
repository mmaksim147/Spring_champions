package org.example.compatibilityservice.controller;

import org.example.compatibilityservice.model.CompatibilityRule;
import org.example.compatibilityservice.repository.CompatibilityRuleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rules")
public class CompatibilityRuleController {

    private final CompatibilityRuleRepository repository;

    public CompatibilityRuleController(CompatibilityRuleRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<List<CompatibilityRule>> getAll() {
        return ResponseEntity.ok(repository.findAll());
    }

    @PostMapping
    public ResponseEntity<CompatibilityRule> create(@RequestBody CompatibilityRule rule) {
        return ResponseEntity.ok(repository.save(rule));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}