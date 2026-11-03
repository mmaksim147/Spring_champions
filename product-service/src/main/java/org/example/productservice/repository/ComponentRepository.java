package org.example.productservice.repository;

import org.example.productservice.model.Component;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComponentRepository extends JpaRepository<Component, Long> {

    Page<Component> findByNameContainingIgnoreCase(String name, Pageable pageable);

    List<Component> findByNameContainingIgnoreCase(String name);

    List<Component> findByCategoryId(Long categoryId);
}