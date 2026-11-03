package org.example.productservice.repository;

import org.example.productservice.model.Build;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BuildRepository extends JpaRepository<Build, Long> {
    List<Build> findByUserId(Long userId);
}