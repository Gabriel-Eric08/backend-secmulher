package com.secmulher.modules.core.domain.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

import com.secmulher.modules.core.infra.entities.CategoryActivityType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class ActivityTypeDtos {
    private ActivityTypeDtos() {}

    // key não pode ser alterada depois de criada (mesma regra dos campos personalizados)
    public record ActivityTypeRequest(
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "[a-z][a-z0-9_]*", message = "use apenas letras minúsculas, números e '_', começando por letra")
        String key,
        @NotBlank @Size(max = 255) String label,
        String description,
        @Size(max = 20)
        @Pattern(regexp = "#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})", message = "use uma cor hexadecimal, ex.: #5b2a86")
        String color,
        @PositiveOrZero Integer position,
        Boolean isActive
    ) {}

    public record ActivityTypeResponse(
        UUID id,
        UUID categoryId,
        String key,
        String label,
        String description,
        String color,
        Integer position,
        Boolean isActive,
        LocalDateTime createdAt
    ) {
        public static ActivityTypeResponse from(CategoryActivityType type) {
            return new ActivityTypeResponse(
                type.getId(),
                type.getCategory().getId(),
                type.getKey(),
                type.getLabel(),
                type.getDescription(),
                type.getColor(),
                type.getPosition(),
                type.getIsActive(),
                type.getCreatedAt()
            );
        }
    }

    // Resumo usado na linha do tempo e no /auth/me
    public record ActivityTypeSummary(
        UUID id,
        String key,
        String label,
        String color
    ) {
        public static ActivityTypeSummary from(CategoryActivityType type) {
            return new ActivityTypeSummary(type.getId(), type.getKey(), type.getLabel(), type.getColor());
        }
    }
}
