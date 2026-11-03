package org.example.compatibilityservice.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "compatibility_checks_log")
public class CompatibilityCheckLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long sourceComponentId;
    private String targetCategory;
    private LocalDateTime checkedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSourceComponentId() { return sourceComponentId; }
    public void setSourceComponentId(Long sourceComponentId) { this.sourceComponentId = sourceComponentId; }
    public String getTargetCategory() { return targetCategory; }
    public void setTargetCategory(String targetCategory) { this.targetCategory = targetCategory; }
    public LocalDateTime getCheckedAt() { return checkedAt; }
    public void setCheckedAt(LocalDateTime checkedAt) { this.checkedAt = checkedAt; }
}