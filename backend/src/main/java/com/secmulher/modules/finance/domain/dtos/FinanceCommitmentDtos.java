package com.secmulher.modules.finance.domain.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.secmulher.modules.finance.domain.dtos.FinanceOperationDtos.OperationResponse;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

public final class FinanceCommitmentDtos {
    private FinanceCommitmentDtos() {}

    private static final String AMOUNT_MIN = "deve ser maior que zero";
    private static final String AMOUNT_DIGITS = "use no máximo 13 dígitos e 2 casas decimais";

    // Situação derivada dos valores do empenho
    public enum CommitmentStatus {
        TO_LIQUIDATE,   // há valor a liquidar
        TO_PAY,         // tudo liquidado, há valor a pagar
        PAID,           // tudo liquidado e pago
        CANCELLED       // anulado por inteiro, sem nada liquidado
    }

    // date: data real do empenho (padrão: hoje); não pode ser futura
    public record CommitmentCreateRequest(
        @NotNull UUID subActionId,
        @NotBlank @Size(max = 50) String number,
        @NotBlank @Size(max = 255) String creditor,
        @Size(max = 2000) String description,
        @NotNull @DecimalMin(value = "0.01", message = AMOUNT_MIN)
        @Digits(integer = 13, fraction = 2, message = AMOUNT_DIGITS) BigDecimal amount,
        @PastOrPresent(message = "não pode ser uma data futura") LocalDate date
    ) {}

    // O valor não é editável: muda por anulação
    public record CommitmentUpdateRequest(
        @NotBlank @Size(max = 50) String number,
        @NotBlank @Size(max = 255) String creditor,
        @Size(max = 2000) String description
    ) {}

    // Liquidação, pagamento e anulação. date: data real (padrão: hoje); não pode ser futura
    // nem anterior à data do empenho
    public record CommitmentMovementRequest(
        @NotNull @DecimalMin(value = "0.01", message = AMOUNT_MIN)
        @Digits(integer = 13, fraction = 2, message = AMOUNT_DIGITS) BigDecimal amount,
        @Size(max = 2000) String description,
        @PastOrPresent(message = "não pode ser uma data futura") LocalDate date
    ) {}

    // committedAmount = amount - cancelledAmount; toLiquidate = committed - liquidated; toPay = liquidated - paid
    public record CommitmentResponse(
        UUID id,
        String number,
        LocalDate commitmentDate,
        UUID actionId,
        String actionCode,
        UUID subActionId,
        String subActionCode,
        String creditor,
        String description,
        BigDecimal amount,
        BigDecimal cancelledAmount,
        BigDecimal committedAmount,
        BigDecimal liquidatedAmount,
        BigDecimal paidAmount,
        BigDecimal toLiquidateAmount,
        BigDecimal toPayAmount,
        CommitmentStatus status,
        UUID createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    // Empenho com as suas operações (empenho, anulações, liquidações, pagamentos), mais recente primeiro
    public record CommitmentDetailResponse(
        CommitmentResponse commitment,
        List<OperationResponse> operations
    ) {}
}
