package com.secmulher.modules.core.domain.access;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.secmulher.modules.core.domain.exceptions.ForbiddenException;
import com.secmulher.modules.core.infra.entities.Permission;
import com.secmulher.modules.core.infra.entities.enums.ConfidentialityLevel;

// O que o usuário atual pode fazer em uma categoria.
// readableColumns/writableColumns nulos significam "todas as colunas";
// allowedActivityTypes nulo significa "todos os tipos de registro manual".
public record CategoryAccess(
    UUID categoryId,
    boolean canRead,
    boolean canCreate,
    boolean canUpdate,
    boolean canDelete,
    Set<String> readableColumns,
    Set<String> writableColumns,
    ConfidentialityLevel maxConfidentiality,
    Set<String> allowedActivityTypes
) {
    public static CategoryAccess full(UUID categoryId) {
        return new CategoryAccess(categoryId, true, true, true, true, null, null, ConfidentialityLevel.CONFIDENTIAL, null);
    }

    public static CategoryAccess none(UUID categoryId) {
        return new CategoryAccess(categoryId, false, false, false, false, Set.of(), Set.of(),
            ConfidentialityLevel.INTERNAL, Set.of());
    }

    public boolean canRegisterActivityType(String key) {
        return canUpdate && (allowedActivityTypes == null || allowedActivityTypes.contains(key));
    }

    public static CategoryAccess from(Permission permission) {
        Map<String, Object> columns = permission.getAllowedColumns() != null ? permission.getAllowedColumns() : Map.of();
        return new CategoryAccess(
            permission.getCategory().getId(),
            Boolean.TRUE.equals(permission.getCanRead()),
            Boolean.TRUE.equals(permission.getCanCreate()),
            Boolean.TRUE.equals(permission.getCanUpdate()),
            Boolean.TRUE.equals(permission.getCanDelete()),
            columnSet(columns.get("read")),
            columnSet(columns.get("write")),
            permission.getMaxConfidentiality() != null ? permission.getMaxConfidentiality() : ConfidentialityLevel.INTERNAL,
            permission.getAllowedActivityTypes() != null ? Set.copyOf(permission.getAllowedActivityTypes()) : null
        );
    }

    private static Set<String> columnSet(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return null;
        }
        return collection.stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());
    }

    public CategoryAccess requireRead() {
        if (!canRead) {
            throw new ForbiddenException("Sem permissão de leitura nesta categoria");
        }
        return this;
    }

    public CategoryAccess requireCreate() {
        if (!canCreate) {
            throw new ForbiddenException("Sem permissão para criar registros nesta categoria");
        }
        return this;
    }

    public CategoryAccess requireUpdate() {
        if (!canUpdate) {
            throw new ForbiddenException("Sem permissão para editar registros nesta categoria");
        }
        return this;
    }

    public CategoryAccess requireDelete() {
        if (!canDelete) {
            throw new ForbiddenException("Sem permissão para excluir registros nesta categoria");
        }
        return this;
    }

    public boolean canReadColumn(String column) {
        return readableColumns == null || readableColumns.contains(column);
    }

    public boolean canWriteColumn(String column) {
        return writableColumns == null || writableColumns.contains(column);
    }

    public boolean canAccess(ConfidentialityLevel level) {
        return maxConfidentiality.allows(level);
    }

    public void requireConfidentiality(ConfidentialityLevel level) {
        if (!canAccess(level)) {
            throw new ForbiddenException("Sem acesso a documentos com nível de sigilo " + level);
        }
    }

    public Map<String, Object> filterReadable(Map<String, Object> values) {
        Map<String, Object> filtered = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (canReadColumn(key)) {
                filtered.put(key, value);
            }
        });
        return filtered;
    }

    public List<String> hiddenColumns(Collection<String> columns) {
        return columns.stream().filter(column -> !canReadColumn(column)).sorted().toList();
    }
}
