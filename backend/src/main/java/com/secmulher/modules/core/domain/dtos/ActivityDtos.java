package com.secmulher.modules.core.domain.dtos;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import com.secmulher.modules.core.domain.dtos.ActivityTypeDtos.ActivityTypeSummary;
import com.secmulher.modules.core.infra.entities.ActivityLedger;
import com.secmulher.modules.core.infra.entities.CategoryActivityType;
import com.secmulher.modules.core.infra.entities.enums.ActivityType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

public final class ActivityDtos {
    private ActivityDtos() {}

    // Registro manual: activityTypeId = um dos tipos ativos da categoria do registro.
    // actionType (TRIAGE, SERVICE, FOLLOW_UP, NOTE) ainda é aceito por compatibilidade e
    // equivale ao tipo padrão correspondente da categoria; envie um dos dois.
    public record ManualActivityRequest(
        UUID activityTypeId,
        @Deprecated ActivityType actionType,
        @NotBlank String description,
        @Size(max = 255) String responsibleEmployee,
        @PastOrPresent LocalDateTime occurredAt
    ) {}

    // activityType: tipo do registro manual (label = nome gravado no momento do registro); null nos automáticos
    public record ActivityResponse(
        UUID id,
        String entityType,
        UUID entityId,
        UUID userId,
        String userName,
        ActivityType actionType,
        ActivityTypeSummary activityType,
        String description,
        Map<String, Object> changes,
        String responsibleEmployee,
        LocalDateTime occurredAt,
        LocalDateTime createdAt,
        boolean redacted
    ) {
        public static ActivityResponse from(ActivityLedger activity) {
            CategoryActivityType type = activity.getActivityType();
            ActivityTypeSummary summary = type == null ? null : new ActivityTypeSummary(
                type.getId(),
                type.getKey(),
                activity.getActivityTypeLabel() != null ? activity.getActivityTypeLabel() : type.getLabel(),
                type.getColor());
            return new ActivityResponse(
                activity.getId(),
                activity.getEntityType(),
                activity.getEntityId(),
                activity.getUser() != null ? activity.getUser().getId() : null,
                activity.getUser() != null ? activity.getUser().getName() : null,
                activity.getActionType(),
                summary,
                activity.getDescription(),
                activity.getChanges(),
                activity.getResponsibleEmployee(),
                activity.getOccurredAt(),
                activity.getCreatedAt(),
                false
            );
        }

        // Cópia com conteúdo ocultado para quem não tem acesso a parte das informações
        public ActivityResponse redact(String newDescription, Map<String, Object> newChanges) {
            return new ActivityResponse(id, entityType, entityId, userId, userName, actionType, activityType,
                newDescription, newChanges, responsibleEmployee, occurredAt, createdAt, true);
        }
    }
}
