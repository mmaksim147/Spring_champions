
package org.example.productservice.repository;

import org.example.productservice.model.BuildItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildItemRepository extends JpaRepository<BuildItem, Long> {

    boolean existsByComponentId(Long componentId);
}
