package org.example.compatibilityservice.controller;

import org.example.compatibilityservice.client.ProductClient;
import org.example.compatibilityservice.dto.ComponentDto;
import org.example.compatibilityservice.model.CompatibilityCheckLog;
import org.example.compatibilityservice.repository.CompatibilityCheckLogRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/check")
public class CompatibilityCheckController {

    private final ProductClient productClient;
    private final CompatibilityCheckLogRepository logRepository;

    public CompatibilityCheckController(ProductClient productClient, CompatibilityCheckLogRepository logRepository) {
        this.productClient = productClient;
        this.logRepository = logRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> checkCompatibility(
            @RequestParam Long sourceId,
            @RequestParam Long targetId,
            @RequestParam String fieldToMatch) {

        ComponentDto source = productClient.getComponentById(sourceId);
        ComponentDto target = productClient.getComponentById(targetId);

        boolean isCompatible = false;
        String reason = "";

        if ("socket".equals(fieldToMatch)) {
            isCompatible = source.getSocket() != null && source.getSocket().equals(target.getSocket());
            reason = "Сокеты " + (isCompatible ? "совпадают" : "не совпадают");
        } else if ("ramType".equals(fieldToMatch)) {
            isCompatible = source.getRamType() != null && source.getRamType().equals(target.getRamType());
            reason = "Типы памяти " + (isCompatible ? "совпадают" : "не совпадают");
        } else if ("formFactor".equals(fieldToMatch)) {
            isCompatible = source.getFormFactor() != null && source.getFormFactor().equals(target.getFormFactor());
            reason = "Форм-факторы " + (isCompatible ? "совпадают" : "не совпадают");
        }

        CompatibilityCheckLog log = new CompatibilityCheckLog();
        log.setSourceComponentId(sourceId);
        log.setTargetCategory(fieldToMatch);
        logRepository.save(log);

        return ResponseEntity.ok(Map.of(
                "source", source.getName(),
                "target", target.getName(),
                "checkedField", fieldToMatch,
                "compatible", isCompatible,
                "reason", reason
        ));
    }
}