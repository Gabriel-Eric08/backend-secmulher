package com.secmulher.modules.core.infra.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.secmulher.modules.core.infra.entities.CategoryActivityType;

public interface CategoryActivityTypeRepository extends JpaRepository<CategoryActivityType, UUID> {
    List<CategoryActivityType> findByCategoryIdOrderByPositionAscLabelAsc(UUID categoryId);

    List<CategoryActivityType> findByCategoryIdAndIsActiveTrueOrderByPositionAscLabelAsc(UUID categoryId);

    Optional<CategoryActivityType> findByCategoryIdAndKey(UUID categoryId, String key);

    boolean existsByCategoryIdAndKey(UUID categoryId, String key);
}
