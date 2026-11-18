package com.secmulher.modules.core.domain.dtos;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.secmulher.modules.core.domain.dtos.ActivityTypeDtos.ActivityTypeSummary;
import com.secmulher.modules.core.domain.dtos.UserDtos.UserResponse;
import com.secmulher.modules.core.infra.entities.enums.ConfidentialityLevel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password
    ) {}

    public record LoginResponse(
        String token,
        String tokenType,
        Instant expiresAt,
        UserResponse user
    ) {}

    public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 6, max = 255) String newPassword
    ) {}

    // Acesso efetivo por categoria, para o front montar menus e telas sem chamadas extras
    public record CategoryAccessResponse(
        UUID categoryId,
        String categoryName,
        String entityType,
        String tableReference,
        boolean canRead,
        boolean canCreate,
        boolean canUpdate,
        boolean canDelete,
        Map<String, Object> allowedColumns,
        ConfidentialityLevel maxConfidentiality,
        List<ActivityTypeSummary> activityTypes  // tipos de registro manual que o usuário pode registrar
    ) {}

    public record MeResponse(
        UserResponse user,
        List<CategoryAccessResponse> access
    ) {}
}
