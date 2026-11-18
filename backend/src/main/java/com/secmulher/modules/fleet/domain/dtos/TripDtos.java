package com.secmulher.modules.fleet.domain.dtos;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.secmulher.modules.fleet.domain.dtos.DriverDtos.DriverResponse;
import com.secmulher.modules.fleet.domain.dtos.DriverDtos.DriverSummary;
import com.secmulher.modules.fleet.domain.dtos.VehicleDtos.VehicleResponse;
import com.secmulher.modules.fleet.domain.dtos.VehicleDtos.VehicleSummary;
import com.secmulher.modules.fleet.infra.entities.enums.ParticipantType;
import com.secmulher.modules.fleet.infra.entities.enums.TripStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class TripDtos {
    private TripDtos() {}

    // USER: userId obrigatório; CLIENT: clientId obrigatório; EXTERNAL: name obrigatório.
    // phone, document e notes são opcionais em todos os tipos
    public record ParticipantRequest(
        @NotNull ParticipantType type,
        UUID userId,
        UUID clientId,
        @Size(max = 255) String name,
        @Size(max = 50) String phone,
        @Size(max = 50) String document,
        @Size(max = 1000) String notes
    ) {}

    // Datas e horas no formato AAAA-MM-DDTHH:MM[:SS]; returnAt precisa ser depois de departureAt.
    // Em PUT, participants substitui a lista inteira
    public record TripRequest(
        @NotNull UUID driverId,
        @NotNull UUID vehicleId,
        @NotBlank @Size(max = 255) String purpose,
        @Size(max = 4000) String description,
        @NotBlank @Size(max = 255) String departureLocation,
        @NotBlank @Size(max = 255) String destination,
        @NotNull LocalDateTime departureAt,
        @NotNull LocalDateTime returnAt,
        List<@Valid @NotNull ParticipantRequest> participants
    ) {}

    // actualDepartureAt opcional (padrão: agora)
    public record StartTripRequest(
        @NotNull @Min(value = 0, message = "não pode ser negativo") Integer startOdometerKm,
        LocalDateTime actualDepartureAt
    ) {}

    // actualReturnAt opcional (padrão: agora)
    public record CompleteTripRequest(
        @NotNull @Min(value = 0, message = "não pode ser negativo") Integer endOdometerKm,
        LocalDateTime actualReturnAt,
        @Size(max = 4000) String notes
    ) {}

    public record CancelTripRequest(
        @NotBlank @Size(max = 2000) String reason
    ) {}

    // hidden = true: atendida cujo nome o usuário não pode ver (name, phone e document vêm nulos)
    public record ParticipantResponse(
        UUID id,
        ParticipantType type,
        UUID userId,
        UUID clientId,
        String name,
        String phone,
        String document,
        String notes,
        boolean hidden
    ) {}

    public record TripResponse(
        UUID id,
        Long number,
        TripStatus status,
        String statusLabel,
        String purpose,
        String description,
        String departureLocation,
        String destination,
        LocalDateTime departureAt,
        LocalDateTime returnAt,
        DriverSummary driver,
        VehicleSummary vehicle,
        int participantCount,
        List<ParticipantResponse> participants,
        LocalDateTime actualDepartureAt,
        LocalDateTime actualReturnAt,
        Integer startOdometerKm,
        Integer endOdometerKm,
        Integer distanceKm,
        String completionNotes,
        String cancellationReason,
        LocalDateTime cancelledAt,
        UUID cancelledBy,
        UUID createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    // Motoristas e veículos livres em [start, end): ativos, sem viagem no intervalo e,
    // no caso do motorista, com CNH válida até o fim do intervalo
    public record AvailabilityResponse(
        LocalDateTime start,
        LocalDateTime end,
        List<DriverResponse> drivers,
        List<VehicleResponse> vehicles
    ) {}
}
