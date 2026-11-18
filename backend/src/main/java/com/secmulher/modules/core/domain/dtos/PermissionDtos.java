package com.secmulher.modules.core.domain.dtos;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.secmulher.modules.core.infra.entities.Permission;
import com.secmulher.modules.core.infra.entities.enums.ConfidentialityLevel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class PermissionDtos {
    private PermissionDtos() {}

    // allowedColumns: {"read": [...], "write": [...]}; sem a chave, todas as colunas são liberadas
    // allowedActivityTypes: keys dos tipos de registro manual liberados; [] remove a restrição (todos)
    public record PermissionRequest(
        @NotNull UUID userId,
        @NotNull UUID categoryId,
        Boolean canRead,
        Boolean canCreate,
        Boolean canUpdate,
        Boolean canDelete,
        Map<String, Object> allowedColumns,
        ConfidentialityLevel maxConfidentiality,
        List<@NotBlank String> allowedActivityTypes
    ) {}

    public record PermissionResponse(
        UUID id,
        UUID userId,
        UUID categoryId,
        Boolean canRead,
        Boolean canCreate,
        Boolean canUpdate,
        Boolean canDelete,
        Map<String, Object> allowedColumns,
        ConfidentialityLevel maxConfidentiality,
        List<String> allowedActivityTypes,  // null = todos os tipos
        LocalDateTime createdAt
    ) {
        public static PermissionResponse from(Permission permission) {
            return new PermissionResponse(
                permission.getId(),
                permission.getUser().getId(),
                permission.getCategory().getId(),
                permission.getCanRead(),
                permission.getCanCreate(),
                permission.getCanUpdate(),
                permission.getCanDelete(),
                permission.getAllowedColumns(),
                permission.getMaxConfidentiality(),
                permission.getAllowedActivityTypes(),
                permission.getCreatedAt()
            );
        }
    }
}
