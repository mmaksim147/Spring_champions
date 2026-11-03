package org.example.compatibilityservice.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "compatibility_rules")
public class CompatibilityRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String sourceCategory;
    @NotBlank
    private String targetCategory;
    @NotBlank
    private String matchingField;
    private String description;

    public CompatibilityRule() {}

    public CompatibilityRule(String sourceCategory, String targetCategory, String matchingField, String description) {
        this.sourceCategory = sourceCategory;
        this.targetCategory = targetCategory;
        this.matchingField = matchingField;
        this.description = description;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSourceCategory() { return sourceCategory; }
    public void setSourceCategory(String sourceCategory) { this.sourceCategory = sourceCategory; }
    public String getTargetCategory() { return targetCategory; }
    public void setTargetCategory(String targetCategory) { this.targetCategory = targetCategory; }
    public String getMatchingField() { return matchingField; }
    public void setMatchingField(String matchingField) { this.matchingField = matchingField; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}