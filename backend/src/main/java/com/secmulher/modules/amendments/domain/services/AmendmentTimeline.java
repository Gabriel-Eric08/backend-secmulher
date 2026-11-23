package com.secmulher.modules.amendments.domain.services;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.secmulher.modules.amendments.infra.entities.Amendment;
import com.secmulher.modules.amendments.infra.entities.AmendmentActivity;
import com.secmulher.modules.amendments.infra.entities.AmendmentPendency;
import com.secmulher.modules.amendments.infra.entities.enums.AmendmentActivityKind;
import com.secmulher.modules.amendments.infra.entities.enums.AmendmentStatus;
import com.secmulher.modules.amendments.infra.entities.enums.PendencyKind;
import com.secmulher.modules.amendments.infra.repositories.AmendmentActivityRepository;
import com.secmulher.modules.amendments.infra.repositories.AmendmentPendencyRepository;
import com.secmulher.modules.core.domain.exceptions.FieldValidationException;
import com.secmulher.modules.core.infra.entities.User;

// Linha do tempo e situação da emenda. Toda atividade passa por aqui: grava o registro imutável e avança
// last_activity_at (nunca para trás, mesmo com registro retroativo).
@Component
public class AmendmentTimeline {
    // Tolerância para relógios fora de sincronia ao validar datas informadas
    private static final long FUTURE_TOLERANCE_MINUTES = 5;

    final AmendmentActivityRepository activityRepository;
    final AmendmentPendencyRepository pendencyRepository;

    public AmendmentTimeline(AmendmentActivityRepository activityRepository,
            AmendmentPendencyRepository pendencyRepository) {
        this.activityRepository = activityRepository;
        this.pendencyRepository = pendencyRepository;
    }

    // Data informada (ou agora), sem aceitar data no futuro
    static LocalDateTime when(LocalDateTime informed, String field) {
        LocalDateTime now = LocalDateTime.now();
        if (informed == null) {
            return now;
        }
        if (informed.isAfter(now.plusMinutes(FUTURE_TOLERANCE_MINUTES))) {
            throw new FieldValidationException("Dados inválidos", Map.of(field, "não pode ser no futuro"));
        }
        return informed;
    }

    AmendmentActivity record(Amendment amendment, AmendmentActivityKind kind, String description,
            Map<String, Object> changes, AmendmentPendency pendency, User user, LocalDateTime occurredAt) {
        AmendmentActivity activity = AmendmentActivity.builder()
            .amendment(amendment)
            .kind(kind)
            .description(description)
            .changes(changes == null || changes.isEmpty() ? null : changes)
            .pendency(pendency)
            .user(user)
            .occurredAt(occurredAt)
            .build();
        if (amendment.getLastActivityAt() == null || occurredAt.isAfter(amendment.getLastActivityAt())) {
            amendment.setLastActivityAt(occurredAt);
        }
        return activityRepository.save(activity);
    }

    // Recalcula a situação de uma emenda aberta pelas pendências abertas e devolve a mudança
    // ({"status": {"antigo", "novo"}}) para entrar no registro da atividade. Concluída/cancelada não muda.
    Map<String, Object> refreshStatus(Amendment amendment) {
        Map<String, Object> changes = new LinkedHashMap<>();
        if (amendment.getStatus() != null && amendment.getStatus().isClosed()) {
            return changes;
        }
        pendencyRepository.flush();
        AmendmentStatus next;
        if (pendencyRepository.existsByAmendmentIdAndKindAndResolvedAtIsNull(amendment.getId(), PendencyKind.IMPEDIMENT)) {
            next = AmendmentStatus.BLOCKED;
        } else if (pendencyRepository.existsByAmendmentIdAndKindAndResolvedAtIsNull(amendment.getId(), PendencyKind.WAITING)) {
            next = AmendmentStatus.WAITING;
        } else {
            next = AmendmentStatus.IN_PROGRESS;
        }
        trackStatus(changes, amendment.getStatus(), next);
        amendment.setStatus(next);
        return changes;
    }

    static void trackStatus(Map<String, Object> changes, AmendmentStatus before, AmendmentStatus after) {
        if (Objects.equals(before, after)) {
            return;
        }
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("antigo", before != null ? before.name() : null);
        change.put("novo", after != null ? after.name() : null);
        changes.put("status", change);
    }
}
