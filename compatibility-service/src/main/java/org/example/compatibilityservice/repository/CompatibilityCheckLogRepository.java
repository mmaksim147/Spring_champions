package org.example.compatibilityservice.repository;

import org.example.compatibilityservice.model.CompatibilityCheckLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompatibilityCheckLogRepository extends JpaRepository<CompatibilityCheckLog, Long> {
}