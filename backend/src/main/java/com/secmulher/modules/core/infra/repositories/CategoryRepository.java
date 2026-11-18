package com.secmulher.modules.core.infra.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.secmulher.modules.core.infra.entities.Category;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    boolean existsByName(String name);

    Optional<Category> findFirstByTableReferenceOrderByCreatedAtAsc(String tableReference);
}
