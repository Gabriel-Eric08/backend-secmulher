package com.secmulher.modules.core.domain.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

import com.secmulher.modules.core.infra.entities.User;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class UserDtos {
    private UserDtos() {}

    // password é obrigatório na criação; na atualização, se vier nulo mantém o atual
    public record UserRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Email @Size(max = 255) String email,
        @Size(min = 6, max = 255) String password,
        Boolean isGlobalAdmin,
        Boolean isActive
    ) {}

    public record UserResponse(
        UUID id,
        String name,
        String email,
        Boolean isGlobalAdmin,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
        public static UserResponse from(User user) {
            return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getIsGlobalAdmin(),
                user.getIsActive(),
                user.getCreatedAt(),
                user.getUpdatedAt()
            );
        }
    }
}
