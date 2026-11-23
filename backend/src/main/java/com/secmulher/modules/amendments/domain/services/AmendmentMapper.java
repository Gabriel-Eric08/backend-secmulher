package com.secmulher.modules.amendments.domain.services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.ActivityResponse;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.AmendmentResponse;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.DeadlineStatus;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.PendencyResponse;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.UserSummary;
import com.secmulher.modules.amendments.domain.dtos.AmendmentSectorDtos.SectorResponse;
import com.secmulher.modules.amendments.domain.dtos.AmendmentSectorDtos.SectorSummary;
import com.secmulher.modules.amendments.infra.entities.Amendment;
import com.secmulher.modules.amendments.infra.entities.AmendmentActivity;
import com.secmulher.modules.amendments.infra.entities.AmendmentPendency;
import com.secmulher.modules.amendments.infra.entities.AmendmentSector;
import com.secmulher.modules.core.infra.entities.User;

// Conversões para a API e campos calculados (dias parada, prazo da vigência)
@Component
public class AmendmentMapper {
    // Janela de alerta da vigência: vence em até N dias -> NEAR
    private final int deadlineAlertDays;

    public AmendmentMapper(@Value("${app.amendments.deadline-alert-days:30}") int deadlineAlertDays) {
        this.deadlineAlertDays = deadlineAlertDays;
    }

    public int deadlineAlertDays() {
        return deadlineAlertDays;
    }

    static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static UserSummary user(User user) {
        return user != null ? new UserSummary(user.getId(), user.getName()) : null;
    }

    static SectorResponse sector(AmendmentSector sector) {
        return new SectorResponse(sector.getId(), sector.getName(), sector.getDescription(), sector.getIsActive(),
            sector.getCreatedAt(), sector.getUpdatedAt());
    }

    static SectorSummary sectorSummary(AmendmentSector sector) {
        return sector != null ? new SectorSummary(sector.getId(), sector.getName()) : null;
    }

    public DeadlineStatus deadlineStatus(Amendment amendment, LocalDate today) {
        if (amendment.getStatus().isClosed()) {
            return DeadlineStatus.CLOSED;
        }
        if (amendment.getTermEndDate() == null) {
            return DeadlineStatus.NO_TERM;
        }
        long days = ChronoUnit.DAYS.between(today, amendment.getTermEndDate());
        if (days < 0) {
            return DeadlineStatus.EXPIRED;
        }
        return days <= deadlineAlertDays ? DeadlineStatus.NEAR : DeadlineStatus.OK;
    }

    // openPendencies: pendências abertas da emenda, da mais antiga para a mais nova
    public AmendmentResponse amendment(Amendment a, List<AmendmentPendency> openPendencies) {
        LocalDate today = LocalDate.now();
        Long daysToDeadline = a.getTermEndDate() != null ? ChronoUnit.DAYS.between(today, a.getTermEndDate()) : null;
        return new AmendmentResponse(
            a.getId(),
            a.getExerciseYear(),
            a.getCycle(),
            a.getParliamentarian(),
            a.getCategory(),
            a.getAlepeCode(),
            a.getCode(),
            a.getActionCode(),
            a.getActionName(),
            a.getSubAction(),
            a.getExpenseGroup(),
            a.getApplicationModality(),
            a.getBeneficiary(),
            a.getMunicipality(),
            a.getObject(),
            a.getType(),
            a.getUnderChange(),
            a.getCurrentAmount(),
            a.getFinancialStatus(),
            a.getSeplagImpediment(),
            a.getDocumentalStatus(),
            a.getDetails(),
            a.getNotes(),
            a.getTechnicalArea(),
            sectorSummary(a.getSector()),
            user(a.getResponsibleUser()),
            a.getResponsibleName(),
            a.getSeiNumber(),
            a.getTechnicalOpinion(),
            a.getTermNumber(),
            a.getTermEndDate(),
            a.getPfNumber(),
            a.getPfDate(),
            a.getSefazPf(),
            a.getCommitmentNumber(),
            a.getDhNumber(),
            a.getLeNumber(),
            a.getPd(),
            a.getObNumber(),
            a.getPaymentDate(),
            a.getCallNumber(),
            a.getProposalNumber(),
            a.getSituation(),
            a.getStatus(),
            a.getStatus().getLabel(),
            a.getLastActivityAt(),
            ChronoUnit.DAYS.between(a.getLastActivityAt().toLocalDate(), today),
            daysToDeadline,
            deadlineStatus(a, today),
            openPendencies.size(),
            openPendencies.isEmpty() ? null : openPendencies.getFirst().getOpenedAt(),
            a.getCompletedAt(),
            user(a.getCompletedBy()),
            a.getCancelledAt(),
            user(a.getCancelledBy()),
            a.getCreatedBy() != null ? a.getCreatedBy().getId() : null,
            a.getCreatedAt(),
            a.getUpdatedAt()
        );
    }

    static PendencyResponse pendency(AmendmentPendency p) {
        LocalDateTime end = p.getResolvedAt() != null ? p.getResolvedAt() : LocalDateTime.now();
        return new PendencyResponse(
            p.getId(),
            p.getAmendment().getId(),
            p.getKind(),
            p.getKind().getLabel(),
            p.getDescription(),
            p.getWaitingOn(),
            p.isOpen(),
            p.getOpenedAt(),
            user(p.getOpenedBy()),
            p.getResolvedAt(),
            user(p.getResolvedBy()),
            p.getResolution(),
            ChronoUnit.DAYS.between(p.getOpenedAt().toLocalDate(), end.toLocalDate())
        );
    }

    static ActivityResponse activity(AmendmentActivity activity) {
        return new ActivityResponse(
            activity.getId(),
            activity.getAmendment().getId(),
            activity.getKind(),
            activity.getKind().getLabel(),
            activity.getDescription(),
            activity.getChanges(),
            activity.getPendency() != null ? activity.getPendency().getId() : null,
            user(activity.getUser()),
            activity.getOccurredAt(),
            activity.getCreatedAt()
        );
    }
}
