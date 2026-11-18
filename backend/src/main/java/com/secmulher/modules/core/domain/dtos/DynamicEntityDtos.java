package com.secmulher.modules.core.domain.dtos;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.secmulher.modules.core.domain.access.CategoryAccess;
import com.secmulher.modules.core.domain.enums.FilterOperator;
import com.secmulher.modules.core.infra.entities.DynamicEntity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public final class DynamicEntityDtos {
    private DynamicEntityDtos() {}

    public record DynamicEntityRequest(
        @NotNull UUID categoryId,
        @NotNull Map<String, Object> customFields
    ) {}

    // Busca em uma categoria. Todos os critérios são combinados com E:
    // - fields: atalho de igualdade exata ({"regiao": "Sul"})
    // - filters: filtros tipados por operador (ver FilterOperator e CategoryFieldResponse.filterOperators)
    // - sort: ordenação por um campo customizado ou por createdAt/updatedAt (padrão: createdAt desc)
    public record DynamicEntitySearchRequest(
        @NotNull UUID categoryId,
        Map<String, Object> fields,
        List<@Valid @NotNull FieldFilter> filters,
        @Valid SortRequest sort
    ) {}

    // value depende do operador: ausente em IS_EMPTY/NOT_EMPTY, lista em IN, [mínimo, máximo] em BETWEEN
    public record FieldFilter(
        @NotBlank String field,
        @NotNull FilterOperator operator,
        Object value
    ) {}

    public record SortRequest(
        @NotBlank String field,
        @Pattern(regexp = "(?i)asc|desc", message = "deve ser 'asc' ou 'desc'") String direction
    ) {
        public boolean descending() {
            return direction == null || "desc".equalsIgnoreCase(direction);
        }
    }

    // Campos sem permissão de leitura são removidos de customFields e listados em hiddenFields
    public record DynamicEntityResponse(
        UUID id,
        UUID categoryId,
        Map<String, Object> customFields,
        UUID createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<String> hiddenFields
    ) {
        public static DynamicEntityResponse from(DynamicEntity entity, CategoryAccess access) {
            Map<String, Object> fields = entity.getCustomFields() != null ? entity.getCustomFields() : Map.of();
            return new DynamicEntityResponse(
                entity.getId(),
                entity.getCategory().getId(),
                access.filterReadable(fields),
                entity.getCreatedBy() != null ? entity.getCreatedBy().getId() : null,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                access.hiddenColumns(fields.keySet())
            );
        }
    }
}
