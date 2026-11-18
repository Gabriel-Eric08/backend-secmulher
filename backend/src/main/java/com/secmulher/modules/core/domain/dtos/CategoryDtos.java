package com.secmulher.modules.core.domain.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

import com.secmulher.modules.core.infra.entities.Category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CategoryDtos {
    private CategoryDtos() {}

    public record CategoryRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Pattern(regexp = "native|dynamic", message = "deve ser 'native' ou 'dynamic'") String entityType,
        @NotBlank @Size(max = 100) String tableReference,
        Boolean isActive
    ) {}

    public record CategoryResponse(
        UUID id,
        String name,
        String entityType,
        String tableReference,
        Boolean isActive,
        LocalDateTime createdAt
    ) {
        public static CategoryResponse from(Category category) {
            return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getEntityType(),
                category.getTableReference(),
                category.getIsActive(),
                category.getCreatedAt()
            );
        }
    }
}
