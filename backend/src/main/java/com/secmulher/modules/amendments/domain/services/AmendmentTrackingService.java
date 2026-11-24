package com.secmulher.modules.amendments.domain.services;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.modules.amendments.domain.access.AmendmentAccessService;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.ActivityResponse;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.AmendmentResponse;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.CompleteRequest;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.NoteRequest;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.PendencyRequest;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.PendencyResponse;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.ReasonRequest;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.ResolvePendencyRequest;
import com.secmulher.modules.amendments.infra.entities.Amendment;
import com.secmulher.modules.amendments.infra.entities.AmendmentPendency;
import com.secmulher.modules.amendments.infra.entities.enums.AmendmentActivityKind;
import com.secmulher.modules.amendments.infra.entities.enums.AmendmentStatus;
import com.secmulher.modules.amendments.infra.repositories.AmendmentActivityRepository;
import com.secmulher.modules.amendments.infra.repositories.AmendmentPendencyRepository;
import com.secmulher.modules.amendments.infra.repositories.AmendmentRepository;
import com.secmulher.modules.core.domain.exceptions.BusinessException;
import com.secmulher.modules.core.domain.exceptions.NotFoundException;
import com.secmulher.modules.core.infra.entities.User;

// Acompanhamento da emenda pelas técnicas: anotações, pendências (impedimentos e esperas), conclusão,
// cancelamento e reabertura. Cada ação trava a emenda, grava a atividade e recalcula a situação:
//   impedimento aberto -> BLOCKED; senão espera aberta -> WAITING; senão IN_PROGRESS.
// Técnica com setor só age nas emendas do próprio setor e nas que estão sob sua responsabilidade (403).
// Regras:
//   - emenda concluída ou cancelada não recebe pendência nem conclusão (422); anotação é sempre permitida;
//   - concluir exige zero pendências abertas (422);
//   - cancelar resolve automaticamente as pendências abertas;
//   - reabrir só emenda concluída ou cancelada; volta para IN_PROGRESS.
@Service
public class AmendmentTrackingService {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    final AmendmentRepository amendmentRepository;
    final AmendmentPendencyRepository pendencyRepository;
    final AmendmentActivityRepository activityRepository;
    final AmendmentTimeline timeline;
    final AmendmentService amendmentService;
    final AmendmentAccessService access;

    public AmendmentTrackingService(AmendmentRepository amendmentRepository,
            AmendmentPendencyRepository pendencyRepository, AmendmentActivityRepository activityRepository,
            AmendmentTimeline timeline, AmendmentService amendmentService, AmendmentAccessService access) {
        this.amendmentRepository = amendmentRepository;
        this.pendencyRepository = pendencyRepository;
        this.activityRepository = activityRepository;
        this.timeline = timeline;
        this.amendmentService = amendmentService;
        this.access = access;
    }

    // Trava a emenda e confere se ela está no escopo do usuário (403 se for de outro setor)
    private Amendment lockAmendment(UUID id) {
        Amendment amendment = amendmentRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new NotFoundException("Emenda não encontrada: " + id));
        access.requireInScope(amendment);
        return amendment;
    }

    private static void requireOpen(Amendment amendment, String action) {
        if (amendment.getStatus().isClosed()) {
            throw new BusinessException("A emenda está " + amendment.getStatus().getLabel().toLowerCase(Locale.ROOT)
                + " e não pode " + action + ". Reabra-a antes");
        }
    }

    private AmendmentResponse save(Amendment amendment) {
        amendmentRepository.saveAndFlush(amendment);
        return amendmentService.toResponse(amendment);
    }

    // ---------------------------------------------------------------- consultas

    @Transactional(readOnly = true)
    public List<ActivityResponse> timeline(UUID id) {
        access.requireRead();
        amendmentService.getAmendmentInScope(id);
        return activityRepository.findTimeline(id).stream().map(AmendmentMapper::activity).toList();
    }

    // open: true = só abertas, false = só resolvidas, nulo = todas (mais recentes primeiro)
    @Transactional(readOnly = true)
    public List<PendencyResponse> pendencies(UUID id, Boolean open) {
        access.requireRead();
        amendmentService.getAmendmentInScope(id);
        return pendencyRepository.findByAmendmentId(id).stream()
            .filter(p -> open == null || p.isOpen() == open)
            .map(AmendmentMapper::pendency)
            .toList();
    }

    // ---------------------------------------------------------------- atividades

    @Transactional
    public AmendmentResponse addNote(UUID id, NoteRequest request) {
        User user = access.requireUpdate();
        Amendment amendment = lockAmendment(id);
        timeline.record(amendment, AmendmentActivityKind.NOTE, request.description().trim(), null, null, user,
            AmendmentTimeline.when(request.occurredAt(), "occurredAt"));
        return save(amendment);
    }

    @Transactional
    public PendencyResponse openPendency(UUID id, PendencyRequest request) {
        User user = access.requireUpdate();
        Amendment amendment = lockAmendment(id);
        requireOpen(amendment, "receber pendências");
        LocalDateTime openedAt = AmendmentTimeline.when(request.openedAt(), "openedAt");

        AmendmentPendency pendency = pendencyRepository.save(AmendmentPendency.builder()
            .amendment(amendment)
            .kind(request.kind())
            .description(request.description().trim())
            .waitingOn(AmendmentMapper.normalize(request.waitingOn()))
            .openedAt(openedAt)
            .openedBy(user)
            .build());
        Map<String, Object> changes = timeline.refreshStatus(amendment);
        String description = request.kind().getLabel() + ": " + pendency.getDescription()
            + (pendency.getWaitingOn() != null ? " (" + pendency.getWaitingOn() + ")" : "");
        timeline.record(amendment, AmendmentActivityKind.PENDENCY_OPENED, description, changes, pendency, user, openedAt);
        amendmentRepository.saveAndFlush(amendment);
        return AmendmentMapper.pendency(pendency);
    }

    @Transactional
    public PendencyResponse resolvePendency(UUID id, UUID pendencyId, ResolvePendencyRequest request) {
        User user = access.requireUpdate();
        Amendment amendment = lockAmendment(id);
        AmendmentPendency pendency = pendencyRepository.findById(pendencyId)
            .filter(p -> p.getAmendment().getId().equals(id))
            .orElseThrow(() -> new NotFoundException("Pendência não encontrada nesta emenda: " + pendencyId));
        if (!pendency.isOpen()) {
            throw new BusinessException("A pendência já foi resolvida em " + WHEN.format(pendency.getResolvedAt()));
        }
        LocalDateTime resolvedAt = AmendmentTimeline.when(request.resolvedAt(), "resolvedAt");
        if (resolvedAt.isBefore(pendency.getOpenedAt())) {
            throw new BusinessException("A resolução (" + WHEN.format(resolvedAt) + ") não pode ser antes da abertura da "
                + "pendência (" + WHEN.format(pendency.getOpenedAt()) + ")");
        }

        pendency.setResolvedAt(resolvedAt);
        pendency.setResolvedBy(user);
        pendency.setResolution(request.resolution().trim());
        pendencyRepository.save(pendency);
        Map<String, Object> changes = timeline.refreshStatus(amendment);
        timeline.record(amendment, AmendmentActivityKind.PENDENCY_RESOLVED,
            "Pendência resolvida (" + pendency.getKind().getLabel().toLowerCase(Locale.ROOT) + "): "
                + pendency.getResolution(), changes, pendency, user, resolvedAt);
        amendmentRepository.saveAndFlush(amendment);
        return AmendmentMapper.pendency(pendency);
    }

    // ---------------------------------------------------------------- ciclo de vida

    @Transactional
    public AmendmentResponse complete(UUID id, CompleteRequest request) {
        User user = access.requireUpdate();
        Amendment amendment = lockAmendment(id);
        requireOpen(amendment, "ser concluída");
        List<AmendmentPendency> open = pendencyRepository.findByAmendmentIdAndResolvedAtIsNullOrderByOpenedAtAsc(id);
        if (!open.isEmpty()) {
            throw new BusinessException("A emenda tem " + open.size() + " pendência(s) aberta(s) e não pode ser "
                + "concluída. Resolva-as antes (a mais antiga: " + open.getFirst().getDescription() + ")");
        }
        LocalDateTime completedAt = AmendmentTimeline.when(request.completedAt(), "completedAt");

        Map<String, Object> changes = new LinkedHashMap<>();
        AmendmentTimeline.trackStatus(changes, amendment.getStatus(), AmendmentStatus.COMPLETED);
        amendment.setStatus(AmendmentStatus.COMPLETED);
        amendment.setCompletedAt(completedAt);
        amendment.setCompletedBy(user);
        timeline.record(amendment, AmendmentActivityKind.COMPLETED, AmendmentMapper.normalize(request.description()),
            changes, null, user, completedAt);
        return save(amendment);
    }

    @Transactional
    public AmendmentResponse cancel(UUID id, ReasonRequest request) {
        User user = access.requireUpdate();
        Amendment amendment = lockAmendment(id);
        requireOpen(amendment, "ser cancelada");
        LocalDateTime now = LocalDateTime.now();
        String reason = request.reason().trim();

        List<AmendmentPendency> open = pendencyRepository.findByAmendmentIdAndResolvedAtIsNullOrderByOpenedAtAsc(id);
        for (AmendmentPendency pendency : open) {
            pendency.setResolvedAt(now.isBefore(pendency.getOpenedAt()) ? pendency.getOpenedAt() : now);
            pendency.setResolvedBy(user);
            pendency.setResolution("Resolvida no cancelamento da emenda: " + reason);
        }
        pendencyRepository.saveAll(open);

        Map<String, Object> changes = new LinkedHashMap<>();
        AmendmentTimeline.trackStatus(changes, amendment.getStatus(), AmendmentStatus.CANCELLED);
        amendment.setStatus(AmendmentStatus.CANCELLED);
        amendment.setCancelledAt(now);
        amendment.setCancelledBy(user);
        String description = open.isEmpty() ? reason
            : reason + " (" + open.size() + " pendência(s) aberta(s) encerrada(s) junto)";
        timeline.record(amendment, AmendmentActivityKind.CANCELLED, description, changes, null, user, now);
        return save(amendment);
    }

    @Transactional
    public AmendmentResponse reopen(UUID id, ReasonRequest request) {
        User user = access.requireUpdate();
        Amendment amendment = lockAmendment(id);
        if (!amendment.getStatus().isClosed()) {
            throw new BusinessException("A emenda está " + amendment.getStatus().getLabel().toLowerCase(Locale.ROOT)
                + "; só emendas concluídas ou canceladas podem ser reabertas");
        }
        Map<String, Object> changes = new LinkedHashMap<>();
        AmendmentTimeline.trackStatus(changes, amendment.getStatus(), AmendmentStatus.IN_PROGRESS);
        amendment.setStatus(AmendmentStatus.IN_PROGRESS);
        amendment.setCompletedAt(null);
        amendment.setCompletedBy(null);
        amendment.setCancelledAt(null);
        amendment.setCancelledBy(null);
        timeline.record(amendment, AmendmentActivityKind.REOPENED, request.reason().trim(), changes, null, user,
            LocalDateTime.now());
        return save(amendment);
    }
}
