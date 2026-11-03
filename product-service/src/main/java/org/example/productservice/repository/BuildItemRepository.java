package org.example.productservice.repository;

import org.example.productservice.model.BuildItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BuildItemRepository extends JpaRepository<BuildItem, Long> {
    List<BuildItem> findByBuildId(Long buildId);
}