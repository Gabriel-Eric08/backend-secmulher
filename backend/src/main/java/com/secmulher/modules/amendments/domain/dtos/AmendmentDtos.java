package com.secmulher.modules.amendments.domain.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.secmulher.modules.amendments.domain.dtos.AmendmentSectorDtos.SectorSummary;
import com.secmulher.modules.amendments.infra.entities.enums.AmendmentActivityKind;
import com.secmulher.modules.amendments.infra.entities.enums.AmendmentStatus;
import com.secmulher.modules.amendments.infra.entities.enums.PendencyKind;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AmendmentDtos {
    private AmendmentDtos() {}

    // ---------------------------------------------------------------- cadastro

    // Todos os campos da planilha. No PUT, o corpo substitui os valores atuais (campo ausente = vazio).
    // responsibleName vazio com responsibleUserId preenchido usa o nome do usuário.
    // comment: texto opcional que acompanha a alteração na linha do tempo (só no PUT).
    public record AmendmentRequest(
        @NotNull @Min(value = 2000, message = "ano inválido") @Max(value = 2100, message = "ano inválido") Integer exerciseYear,
        @Size(max = 50) String cycle,
        @NotBlank @Size(max = 255) String parliamentarian,
        @Size(max = 100) String category,
        @Size(max = 50) String alepeCode,
        @Size(max = 50) String code,
        @Size(max = 50) String actionCode,
        @Size(max = 255) String actionName,
        @Size(max = 255) String subAction,
        @Size(max = 20) String expenseGroup,
        @Size(max = 20) String applicationModality,
        @Size(max = 255) String beneficiary,
        @Size(max = 150) String municipality,
        @Size(max = 10000) String object,
        @Size(max = 100) String type,
        @Size(max = 255) String underChange,
        @DecimalMin(value = "0", message = "não pode ser negativo")
        @Digits(integer = 13, fraction = 2, message = "até 13 dígitos inteiros e 2 casas decimais") BigDecimal currentAmount,
        @Size(max = 255) String financialStatus,
        @Size(max = 10000) String seplagImpediment,
        @Size(max = 255) String documentalStatus,
        @Size(max = 10000) String details,
        @Size(max = 10000) String notes,
        @Size(max = 255) String technicalArea,
        UUID sectorId,
        UUID responsibleUserId,
        @Size(max = 255) String responsibleName,
        @Size(max = 50) String seiNumber,
        @Size(max = 10000) String technicalOpinion,
        @Size(max = 50) String termNumber,
        LocalDate termEndDate,
        @Size(max = 50) String pfNumber,
        LocalDate pfDate,
        @Size(max = 100) String sefazPf,
        @Size(max = 50) String commitmentNumber,
        @Size(max = 50) String dhNumber,
        @Size(max = 50) String leNumber,
        @Size(max = 100) String pd,
        @Size(max = 50) String obNumber,
        LocalDate paymentDate,
        @Size(max = 50) String callNumber,
        @Size(max = 50) String proposalNumber,
        @Size(max = 255) String situation,
        @Size(max = 2000) String comment
    ) {}

    // Situação do prazo (vigência do Termo de Fomento), calculada na consulta
    public enum DeadlineStatus {
        NO_TERM,   // sem data de vigência
        OK,        // dentro do prazo, fora da janela de alerta
        NEAR,      // vence dentro da janela de alerta (app.amendments.deadline-alert-days)
        EXPIRED,   // vigência já passou e a emenda não foi concluída
        CLOSED     // emenda concluída ou cancelada: o prazo não se aplica mais
    }

    public record UserSummary(UUID id, String name) {}

    public record AmendmentResponse(
        UUID id,
        Integer exerciseYear,
        String cycle,
        String parliamentarian,
        String category,
        String alepeCode,
        String code,
        String actionCode,
        String actionName,
        String subAction,
        String expenseGroup,
        String applicationModality,
        String beneficiary,
        String municipality,
        String object,
        String type,
        String underChange,
        BigDecimal currentAmount,
        String financialStatus,
        String seplagImpediment,
        String documentalStatus,
        String details,
        String notes,
        String technicalArea,
        SectorSummary sector,
        UserSummary responsibleUser,
        String responsibleName,
        String seiNumber,
        String technicalOpinion,
        String termNumber,
        LocalDate termEndDate,
        String pfNumber,
        LocalDate pfDate,
        String sefazPf,
        String commitmentNumber,
        String dhNumber,
        String leNumber,
        String pd,
        String obNumber,
        LocalDate paymentDate,
        String callNumber,
        String proposalNumber,
        String situation,
        // Controle do sistema
        AmendmentStatus status,
        String statusLabel,
        LocalDateTime lastActivityAt,
        Long idleDays,
        Long daysToDeadline,
        DeadlineStatus deadlineStatus,
        Integer openPendencies,
        LocalDateTime oldestOpenPendencyAt,
        LocalDateTime completedAt,
        UserSummary completedBy,
        LocalDateTime cancelledAt,
        UserSummary cancelledBy,
        UUID createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    // ---------------------------------------------------------------- consultas

    // Ordenação da listagem
    public enum AmendmentSort {
        RECENT,           // última atividade mais recente primeiro (padrão)
        IDLE,             // parada há mais tempo primeiro
        DEADLINE,         // vigência mais próxima primeiro (sem vigência no fim)
        OLDEST_PENDENCY,  // pendência aberta mais antiga primeiro (sem pendência no fim)
        AMOUNT,           // maior valor atual primeiro
        PARLIAMENTARIAN   // parlamentar em ordem alfabética
    }

    // Filtros da listagem; todos opcionais e combinados com "e"
    public record AmendmentFilter(
        List<AmendmentStatus> status,
        Boolean open,
        Boolean pending,
        PendencyKind pendencyKind,
        Integer idleDays,
        Integer deadlineWithinDays,
        Boolean expired,
        UUID sectorId,
        UUID responsibleUserId,
        Boolean mine,
        Integer exerciseYear,
        String cycle,
        String parliamentarian,
        String municipality,
        String q
    ) {}

    public record AmendmentSummaryResponse(
        long total,
        Map<AmendmentStatus, Long> byStatus,
        long open,
        long pending,
        long expired,
        long nearDeadline,
        int deadlineAlertDays,
        long idle,
        int idleDays
    ) {}

    // Valores já usados, para os filtros do front
    public record AmendmentOptionsResponse(
        List<Integer> exerciseYears,
        List<String> cycles,
        List<String> parliamentarians,
        List<String> municipalities,
        List<String> categories,
        List<String> types,
        List<String> technicalAreas
    ) {}

    // ---------------------------------------------------------------- acompanhamento

    // occurredAt: quando aconteceu (padrão agora; não pode ser no futuro)
    public record NoteRequest(
        @NotBlank @Size(max = 10000) String description,
        LocalDateTime occurredAt
    ) {}

    public record PendencyRequest(
        @NotNull PendencyKind kind,
        @NotBlank @Size(max = 10000) String description,
        @Size(max = 255) String waitingOn,
        LocalDateTime openedAt
    ) {}

    public record ResolvePendencyRequest(
        @NotBlank @Size(max = 10000) String resolution,
        LocalDateTime resolvedAt
    ) {}

    public record CompleteRequest(
        @Size(max = 10000) String description,
        LocalDateTime completedAt
    ) {}

    // Usado para cancelar e para reabrir
    public record ReasonRequest(
        @NotBlank @Size(max = 10000) String reason
    ) {}

    public record PendencyResponse(
        UUID id,
        UUID amendmentId,
        PendencyKind kind,
        String kindLabel,
        String description,
        String waitingOn,
        boolean open,
        LocalDateTime openedAt,
        UserSummary openedBy,
        LocalDateTime resolvedAt,
        UserSummary resolvedBy,
        String resolution,
        Long daysOpen
    ) {}

    public record ActivityResponse(
        UUID id,
        UUID amendmentId,
        AmendmentActivityKind kind,
        String kindLabel,
        String description,
        Map<String, Object> changes,
        UUID pendencyId,
        UserSummary user,
        LocalDateTime occurredAt,
        LocalDateTime createdAt
    ) {}
}
