package com.secmulher.modules.finance.domain.dtos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class FinanceSubActionDtos {
    private FinanceSubActionDtos() {}

    // A subação nasce com saldo zero; o valor chega por distribuição, TAC ou transferência
    public record FinanceSubActionRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2000) String description,
        Boolean isActive
    ) {}

    // balance = dotação; availableAmount = dotação - empenhado (o que ainda pode ser empenhado ou transferido)
    public record FinanceSubActionResponse(
        UUID id,
        UUID actionId,
        String actionCode,
        String code,
        String name,
        String description,
        Boolean isActive,
        BigDecimal balance,
        BigDecimal committedAmount,
        BigDecimal liquidatedAmount,
        BigDecimal paidAmount,
        BigDecimal availableAmount,
        UUID createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}
}
