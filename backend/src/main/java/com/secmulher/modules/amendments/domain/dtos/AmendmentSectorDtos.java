package com.secmulher.modules.amendments.domain.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AmendmentSectorDtos {
    private AmendmentSectorDtos() {}

    public record SectorRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2000) String description,
        Boolean isActive
    ) {}

    public record SectorResponse(
        UUID id,
        String name,
        String description,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    // Resumo usado dentro da emenda
    public record SectorSummary(UUID id, String name) {}

    // Técnica vinculada ao setor
    public record MemberResponse(
        UUID userId,
        String name,
        String email,
        Boolean isActive,
        LocalDateTime createdAt
    ) {}

    // O que o usuário logado pode fazer no módulo e a que setor está restrito
    public record AccessResponse(
        boolean canRead,
        boolean canCreate,
        boolean canUpdate,
        boolean canDelete,
        boolean restrictedToSector,
        SectorSummary sector,
        boolean canManageSectorMembers
    ) {}
}
