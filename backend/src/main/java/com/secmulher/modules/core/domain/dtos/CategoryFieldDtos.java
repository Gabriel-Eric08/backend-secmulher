package com.secmulher.modules.core.domain.dtos;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.secmulher.modules.core.domain.enums.FilterOperator;
import com.secmulher.modules.core.infra.entities.CategoryField;
import com.secmulher.modules.core.infra.entities.enums.FieldType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class CategoryFieldDtos {
    private CategoryFieldDtos() {}

    // key não pode ser alterada depois de criada, pois já é usada nos registros existentes
    public record CategoryFieldRequest(
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "[a-z][a-z0-9_]*", message = "use apenas letras minúsculas, números e '_', começando por letra")
        String key,
        @NotBlank @Size(max = 255) String label,
        @NotNull FieldType fieldType,
        Boolean isRequired,
        List<@NotBlank String> options,
        @PositiveOrZero Integer position
    ) {}

    public record CategoryFieldResponse(
        UUID id,
        UUID categoryId,
        String key,
        String label,
        FieldType fieldType,
        Boolean isRequired,
        List<String> options,
        Integer position,
        List<FilterOperator> filterOperators,  // operadores aceitos na busca para este tipo de campo
        LocalDateTime createdAt
    ) {
        public static CategoryFieldResponse from(CategoryField field) {
            return new CategoryFieldResponse(
                field.getId(),
                field.getCategory().getId(),
                field.getKey(),
                field.getLabel(),
                field.getFieldType(),
                field.getIsRequired(),
                field.getOptions(),
                field.getPosition(),
                FilterOperator.forType(field.getFieldType()),
                field.getCreatedAt()
            );
        }
    }
}
