package com.secmulher.modules.core.infra.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.secmulher.modules.core.infra.entities.CategoryField;

public interface CategoryFieldRepository extends JpaRepository<CategoryField, UUID> {
    List<CategoryField> findByCategoryIdOrderByPositionAscLabelAsc(UUID categoryId);

    boolean existsByCategoryIdAndKey(UUID categoryId, String key);
}
