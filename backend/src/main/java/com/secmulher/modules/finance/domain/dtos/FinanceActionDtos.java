package com.secmulher.modules.finance.domain.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.secmulher.modules.finance.domain.dtos.FinanceSubActionDtos.FinanceSubActionResponse;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

public final class FinanceActionDtos {
    private FinanceActionDtos() {}

    // initialAmount opcional: vira uma operação "Valor inicial da ação", que fica toda como saldo a distribuir.
    // initialDate: data real desse valor inicial (padrão: hoje)
    public record FinanceActionCreateRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2000) String description,
        @DecimalMin(value = "0.00", message = "não pode ser negativo")
        @Digits(integer = 13, fraction = 2, message = "use no máximo 13 dígitos e 2 casas decimais") BigDecimal initialAmount,
        @PastOrPresent(message = "não pode ser uma data futura") LocalDate initialDate
    ) {}

    // O valor da ação não é editável: ele só muda por operações, que ficam no histórico
    public record FinanceActionUpdateRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2000) String description,
        Boolean isActive
    ) {}

    // totalAmount = distributedAmount (soma das subações) + undistributedAmount (saldo a distribuir).
    // committed/liquidated/paid somam as subações; availableAmount = totalAmount - committedAmount
    public record FinanceActionResponse(
        UUID id,
        String code,
        String name,
        String description,
        Boolean isActive,
        BigDecimal totalAmount,
        BigDecimal distributedAmount,
        BigDecimal undistributedAmount,
        BigDecimal committedAmount,
        BigDecimal liquidatedAmount,
        BigDecimal paidAmount,
        BigDecimal availableAmount,
        UUID createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<FinanceSubActionResponse> subActions
    ) {}
}
