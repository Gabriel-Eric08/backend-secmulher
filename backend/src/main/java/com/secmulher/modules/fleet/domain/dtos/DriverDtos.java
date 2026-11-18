package com.secmulher.modules.fleet.domain.dtos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class DriverDtos {
    private DriverDtos() {}

    // userId opcional: vincula o motorista a um usuário do sistema (um usuário por motorista)
    public record DriverRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 50) String phone,
        @NotBlank @Pattern(regexp = "\\d{9,11}", message = "deve ter de 9 a 11 dígitos") String licenseNumber,
        @NotBlank @Pattern(regexp = "A|B|C|D|E|AB|AC|AD|AE", message = "deve ser A, B, C, D, E, AB, AC, AD ou AE")
        String licenseCategory,
        @NotNull LocalDate licenseExpiry,
        UUID userId,
        @Size(max = 2000) String notes,
        Boolean isActive
    ) {}

    // licenseExpired: CNH vencida hoje
    public record DriverResponse(
        UUID id,
        String name,
        String phone,
        String licenseNumber,
        String licenseCategory,
        LocalDate licenseExpiry,
        boolean licenseExpired,
        UUID userId,
        String userName,
        String notes,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    // Resumo usado dentro da viagem
    public record DriverSummary(UUID id, String name, String phone) {}
}
