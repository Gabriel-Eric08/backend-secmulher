package com.secmulher.modules.core.domain.services;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.modules.core.domain.access.AccessControlService;
import com.secmulher.modules.core.domain.access.CategoryAccess;
import com.secmulher.modules.core.domain.dtos.ActivityDtos.ActivityResponse;
import com.secmulher.modules.core.domain.dtos.ActivityDtos.ManualActivityRequest;
import com.secmulher.modules.core.domain.enums.RecordType;
import com.secmulher.modules.core.domain.exceptions.BusinessException;
import com.secmulher.modules.core.domain.exceptions.FieldValidationException;
import com.secmulher.modules.core.domain.exceptions.ForbiddenException;
import com.secmulher.modules.core.domain.exceptions.NotFoundException;
import com.secmulher.modules.core.infra.entities.ActivityLedger;
import com.secmulher.modules.core.infra.entities.CategoryActivityType;
import com.secmulher.modules.core.infra.repositories.CategoryActivityTypeRepository;
import com.secmulher.modules.core.infra.entities.enums.ActivityType;
import com.secmulher.modules.core.infra.entities.enums.ConfidentialityLevel;
import com.secmulher.modules.core.infra.repositories.ActivityLedgerRepository;
import com.secmulher.modules.core.infra.repositories.UserRepository;
import com.secmulher.modules.core.infra.security.CurrentUserProvider;

@Service
public class ActivityService {
    // Chaves de "changes" que não são colunas e por isso não passam pelo filtro de colunas
    private static final Set<String> NON_COLUMN_KEYS = Set.of("categoria");
    private static final Set<ActivityType> FILE_EVENTS = Set.of(ActivityType.FILE_UPLOADED, ActivityType.FILE_UPDATED,
        ActivityType.FILE_REMOVED, ActivityType.FILE_DOWNLOADED);

    final ActivityLedgerRepository activityLedgerRepository;
    final UserRepository userRepository;
    final RecordService recordService;
    final CurrentUserProvider currentUserProvider;
    final AccessControlService accessControl;
    final CategoryActivityTypeRepository activityTypeRepository;

    public ActivityService(ActivityLedgerRepository activityLedgerRepository, UserRepository userRepository,
            RecordService recordService, CurrentUserProvider currentUserProvider, AccessControlService accessControl,
            CategoryActivityTypeRepository activityTypeRepository) {
        this.activityTypeRepository = activityTypeRepository;
        this.activityLedgerRepository = activityLedgerRepository;
        this.userRepository = userRepository;
        this.recordService = recordService;
        this.currentUserProvider = currentUserProvider;
        this.accessControl = accessControl;
    }

    // Adiciona {"campo": {"antigo": ..., "novo": ...}} ao mapa de mudanças, se o valor mudou
    public static void trackChange(Map<String, Object> changes, String field, Object before, Object after) {
        if (Objects.equals(before, after)) {
            return;
        }
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("antigo", before != null ? String.valueOf(before) : null);
        change.put("novo", after != null ? String.valueOf(after) : null);
        changes.put(field, change);
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> getTimeline(RecordType type, UUID recordId) {
        recordService.ensureExists(type, recordId);
        CategoryAccess access = accessControl.forRecord(type, recordId).requireRead();
        return activityLedgerRepository
            .findByEntityTypeAndEntityIdOrderByOccurredAtDescCreatedAtDesc(type.getTableName(), recordId)
            .stream()
            .map(activity -> applyAccess(ActivityResponse.from(activity), access))
            .toList();
    }

    // Auditoria: o administrador vê as ações de qualquer usuário; cada usuário vê as próprias
    @Transactional(readOnly = true)
    public List<ActivityResponse> getByUser(UUID userId) {
        accessControl.requireAdminOrSelf(userId);
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Usuário não encontrado: " + userId);
        }
        return activityLedgerRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream().map(ActivityResponse::from).toList();
    }

    // Registrar atendimento faz parte de editar o acompanhamento do registro. O tipo precisa ser um dos
    // tipos ativos da categoria do registro e estar liberado na permissão do usuário.
    @Transactional
    public ActivityResponse createManual(RecordType type, UUID recordId, ManualActivityRequest request) {
        if (request.activityTypeId() == null && request.actionType() == null) {
            throw new FieldValidationException("Dados inválidos",
                Map.of("activityTypeId", "informe o tipo de registro (activityTypeId)"));
        }
        if (request.activityTypeId() != null && request.actionType() != null) {
            throw new FieldValidationException("Dados inválidos",
                Map.of("actionType", "envie apenas activityTypeId; actionType é o formato antigo"));
        }
        if (request.actionType() != null && request.actionType().legacyDefaultKey() == null) {
            throw new BusinessException("O tipo " + request.actionType() + " não pode ser usado em registros manuais; "
                + "use activityTypeId");
        }
        recordService.ensureExists(type, recordId);
        CategoryAccess access = accessControl.forRecord(type, recordId).requireUpdate();

        CategoryActivityType activityType = resolveActivityType(type, recordId, request);
        if (!access.canRegisterActivityType(activityType.getKey())) {
            throw new ForbiddenException("Sem permissão para registrar '" + activityType.getLabel() + "' nesta categoria");
        }

        ActivityLedger activity = ActivityLedger.builder()
            .entityType(type.getTableName())
            .entityId(recordId)
            .user(currentUserProvider.getCurrentUser().orElse(null))
            .actionType(ActivityType.MANUAL)
            .activityType(activityType)
            .activityTypeLabel(activityType.getLabel())
            .description(request.description())
            .responsibleEmployee(request.responsibleEmployee())
            .occurredAt(Objects.requireNonNullElseGet(request.occurredAt(), LocalDateTime::now))
            .build();

        return ActivityResponse.from(activityLedgerRepository.saveAndFlush(activity));
    }

    private CategoryActivityType resolveActivityType(RecordType type, UUID recordId, ManualActivityRequest request) {
        UUID categoryId = accessControl.categoryIdForRecord(type, recordId);
        if (categoryId == null) {
            throw new BusinessException("O registro não pertence a nenhuma categoria, então não há tipos de registro. "
                + "Crie a categoria nativa com tableReference '" + type.getTableName() + "'");
        }

        CategoryActivityType activityType = request.activityTypeId() != null
            ? activityTypeRepository.findById(request.activityTypeId())
                .filter(found -> found.getCategory().getId().equals(categoryId))
                .orElseThrow(() -> new BusinessException("Tipo de registro não pertence à categoria deste registro: "
                    + request.activityTypeId()))
            : activityTypeRepository.findByCategoryIdAndKey(categoryId, request.actionType().legacyDefaultKey())
                .orElseThrow(() -> new BusinessException("A categoria deste registro não tem o tipo padrão '"
                    + request.actionType().legacyDefaultKey() + "' equivalente a " + request.actionType()
                    + "; use activityTypeId"));

        if (!Boolean.TRUE.equals(activityType.getIsActive())) {
            throw new BusinessException("O tipo de registro '" + activityType.getLabel() + "' está desativado");
        }
        return activityType;
    }

    // Registro automático de auditoria; participa da transação de quem chamou
    @Transactional
    public void record(RecordType type, UUID recordId, ActivityType actionType, String description,
            Map<String, Object> changes) {
        ActivityLedger activity = ActivityLedger.builder()
            .entityType(type.getTableName())
            .entityId(recordId)
            .user(currentUserProvider.getCurrentUser().orElse(null))
            .actionType(actionType)
            .description(description)
            .changes(changes == null || changes.isEmpty() ? null : changes)
            .occurredAt(LocalDateTime.now())
            .build();

        activityLedgerRepository.save(activity);
    }

    // O histórico não pode vazar o que o usuário não pode ver: valores de colunas ocultas
    // e nomes de documentos acima do seu nível de sigilo
    private ActivityResponse applyAccess(ActivityResponse activity, CategoryAccess access) {
        Map<String, Object> changes = activity.changes();
        if (changes == null) {
            return activity;
        }

        if (FILE_EVENTS.contains(activity.actionType())) {
            boolean allowed = Set.of("sigilo", "sigilo_anterior").stream()
                .map(changes::get)
                .filter(Objects::nonNull)
                .allMatch(level -> access.canAccess(ConfidentialityLevel.valueOf(String.valueOf(level))));
            return allowed ? activity : activity.redact("Documento com nível de sigilo acima do seu acesso", null);
        }

        if (access.readableColumns() == null) {
            return activity;
        }
        Map<String, Object> visible = new LinkedHashMap<>();
        changes.forEach((key, value) -> {
            if (access.canReadColumn(key) || NON_COLUMN_KEYS.contains(key)) {
                visible.put(key, value);
            }
        });
        return visible.size() == changes.size() ? activity : activity.redact(activity.description(), visible);
    }
}
