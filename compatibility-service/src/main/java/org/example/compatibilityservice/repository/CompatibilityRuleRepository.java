package org.example.compatibilityservice.repository;

import org.example.compatibilityservice.model.CompatibilityRule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompatibilityRuleRepository extends JpaRepository<CompatibilityRule, Long> {
}