package com.secmulher.modules.finance.domain.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.secmulher.modules.finance.infra.entities.enums.FinanceEntryKind;
import com.secmulher.modules.finance.infra.entities.enums.FinanceOperationMethod;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

public final class FinanceOperationDtos {
    private FinanceOperationDtos() {}

    private static final String AMOUNT_MIN = "deve ser maior que zero";
    private static final String AMOUNT_DIGITS = "use no máximo 13 dígitos e 2 casas decimais";

    private static final String NO_FUTURE = "não pode ser uma data futura";

    // Em todas as operações, date é a data real (padrão: hoje) e não pode ser futura.
    // Método A (mesma ação) e Método B (ações diferentes)
    public record TransferRequest(
        @NotNull UUID fromSubActionId,
        @NotNull UUID toSubActionId,
        @NotNull @DecimalMin(value = "0.01", message = AMOUNT_MIN)
        @Digits(integer = 13, fraction = 2, message = AMOUNT_DIGITS) BigDecimal amount,
        @Size(max = 2000) String description,
        @PastOrPresent(message = NO_FUTURE) LocalDate date
    ) {}

    public record AllocationRequest(
        @NotNull UUID subActionId,
        @NotNull @DecimalMin(value = "0.01", message = AMOUNT_MIN)
        @Digits(integer = 13, fraction = 2, message = AMOUNT_DIGITS) BigDecimal amount
    ) {}

    // Método C: soma das alocações <= amount; o que não for alocado fica como saldo a distribuir da ação
    public record TacRequest(
        @NotNull UUID actionId,
        @NotNull @DecimalMin(value = "0.01", message = AMOUNT_MIN)
        @Digits(integer = 13, fraction = 2, message = AMOUNT_DIGITS) BigDecimal amount,
        @Size(max = 2000) String description,
        List<@Valid @NotNull AllocationRequest> allocations,
        @PastOrPresent(message = NO_FUTURE) LocalDate date
    ) {}

    // Reparte o saldo a distribuir da ação entre as subações dela
    public record DistributionRequest(
        @NotNull UUID actionId,
        @Size(max = 2000) String description,
        @NotEmpty List<@Valid @NotNull AllocationRequest> allocations,
        @PastOrPresent(message = NO_FUTURE) LocalDate date
    ) {}

    // subAction* nulos = lançamento no valor total da ação.
    // kind: BUDGET (dotação), COMMITTED (empenhado), LIQUIDATED (liquidado) ou PAID (pago)
    public record EntryResponse(
        UUID actionId,
        String actionCode,
        String actionName,
        UUID subActionId,
        String subActionCode,
        String subActionName,
        FinanceEntryKind kind,
        BigDecimal amount
    ) {}

    // commitment* preenchidos nas operações de empenho, anulação, liquidação e pagamento.
    // operationDate = data real da operação; registeredAt = quando foi registrada no sistema
    public record OperationResponse(
        UUID id,
        Long number,
        FinanceOperationMethod method,
        String methodLabel,
        BigDecimal amount,
        String description,
        UUID commitmentId,
        String commitmentNumber,
        UUID performedBy,
        String performedByName,
        LocalDate operationDate,
        LocalDateTime registeredAt,
        List<EntryResponse> entries
    ) {}

    // Histórico de uma ação: quanto a operação mudou cada valor e como ele ficou depois
    public record ActionHistoryItem(
        OperationResponse operation,
        BigDecimal totalDelta,
        BigDecimal totalAfter,
        BigDecimal undistributedDelta,
        BigDecimal undistributedAfter,
        BigDecimal committedDelta,
        BigDecimal committedAfter,
        BigDecimal liquidatedDelta,
        BigDecimal liquidatedAfter,
        BigDecimal paidDelta,
        BigDecimal paidAfter,
        BigDecimal availableAfter
    ) {}

    // delta/balanceAfter referem-se à dotação da subação
    public record SubActionHistoryItem(
        OperationResponse operation,
        BigDecimal delta,
        BigDecimal balanceAfter,
        BigDecimal committedDelta,
        BigDecimal committedAfter,
        BigDecimal liquidatedDelta,
        BigDecimal liquidatedAfter,
        BigDecimal paidDelta,
        BigDecimal paidAfter,
        BigDecimal availableAfter
    ) {}
}
