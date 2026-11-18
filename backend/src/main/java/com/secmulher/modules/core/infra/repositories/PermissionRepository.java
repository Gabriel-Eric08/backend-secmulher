package com.secmulher.modules.core.infra.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.secmulher.modules.core.infra.entities.Permission;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    List<Permission> findByUserId(UUID userId);

    List<Permission> findByCategoryId(UUID categoryId);

    Optional<Permission> findByUserIdAndCategoryId(UUID userId, UUID categoryId);

    boolean existsByUserIdAndCategoryId(UUID userId, UUID categoryId);
}
