package com.secmulher.modules.fleet.domain.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class VehicleDtos {
    private VehicleDtos() {}

    // plate: padrão antigo (ABC-1234) ou Mercosul (ABC1D23), com ou sem hífen; é gravada sem hífen e em maiúsculas.
    // capacity: lugares para passageiros, sem contar o motorista
    public record VehicleRequest(
        @NotBlank @Pattern(regexp = "(?i)[A-Z]{3}-?[0-9][A-Z0-9][0-9]{2}", message = "placa inválida (ex.: ABC1D23 ou ABC-1234)")
        String plate,
        @Size(max = 100) String brand,
        @NotBlank @Size(max = 100) String model,
        @Min(value = 1950, message = "ano inválido") @Max(value = 2100, message = "ano inválido") Integer year,
        @Size(max = 50) String color,
        @NotNull @Min(value = 1, message = "deve ser pelo menos 1") @Max(value = 100, message = "deve ser no máximo 100") Integer capacity,
        @Min(value = 0, message = "não pode ser negativo") Integer odometerKm,
        @Size(max = 2000) String notes,
        Boolean isActive
    ) {}

    public record VehicleResponse(
        UUID id,
        String plate,
        String brand,
        String model,
        Integer year,
        String color,
        Integer capacity,
        Integer odometerKm,
        String notes,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    // Resumo usado dentro da viagem
    public record VehicleSummary(UUID id, String plate, String brand, String model, Integer capacity) {}
}
