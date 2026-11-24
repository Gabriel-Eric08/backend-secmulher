package com.secmulher.modules.amendments.domain.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.modules.amendments.domain.access.AmendmentAccessService;
import com.secmulher.modules.amendments.domain.access.AmendmentScope;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.AmendmentFilter;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.AmendmentOptionsResponse;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.AmendmentRequest;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.AmendmentResponse;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.AmendmentSort;
import com.secmulher.modules.amendments.domain.dtos.AmendmentDtos.AmendmentSummaryResponse;
import com.secmulher.modules.amendments.infra.entities.Amendment;
import com.secmulher.modules.amendments.infra.entities.AmendmentPendency;
import com.secmulher.modules.amendments.infra.entities.AmendmentSector;
import com.secmulher.modules.amendments.infra.entities.AmendmentSectorMember;
import com.secmulher.modules.amendments.infra.entities.enums.AmendmentActivityKind;
import com.secmulher.modules.amendments.infra.entities.enums.AmendmentStatus;
import com.secmulher.modules.amendments.infra.entities.enums.PendencyKind;
import com.secmulher.modules.amendments.infra.repositories.AmendmentPendencyRepository;
import com.secmulher.modules.amendments.infra.repositories.AmendmentRepository;
import com.secmulher.modules.amendments.infra.repositories.AmendmentSectorMemberRepository;
import com.secmulher.modules.amendments.infra.repositories.AmendmentSectorRepository;
import com.secmulher.modules.core.domain.exceptions.BusinessException;
import com.secmulher.modules.core.domain.exceptions.ConflictException;
import com.secmulher.modules.core.domain.exceptions.ForbiddenException;
import com.secmulher.modules.core.domain.exceptions.NotFoundException;
import com.secmulher.modules.core.domain.services.ActivityService;
import com.secmulher.modules.core.infra.entities.User;
import com.secmulher.modules.core.infra.repositories.UserRepository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

// Cadastro e consultas de emendas.
//   - código (COD) único no ano, sem diferenciar maiúsculas (409);
//   - setor informado precisa estar ativo quando é atribuído (422);
//   - emenda concluída ou cancelada não é editada: reabra antes (422);
//   - toda alteração de campo vai para a linha do tempo com o valor antigo e o novo;
//   - técnica com setor só alcança as emendas do setor e as que estão sob sua responsabilidade (403),
//     cadastra no próprio setor (sem sectorId = o dela) e não move emenda para outro setor (403);
//   - a técnica responsável, se tiver setor, precisa ser do setor da emenda (422).
@Service
public class AmendmentService {
    private static final List<AmendmentStatus> CLOSED = List.of(AmendmentStatus.COMPLETED, AmendmentStatus.CANCELLED);
    private static final List<AmendmentStatus> PENDING = List.of(AmendmentStatus.WAITING, AmendmentStatus.BLOCKED);
    private static final int DEFAULT_IDLE_DAYS = 30;

    final AmendmentRepository amendmentRepository;
    final AmendmentSectorRepository sectorRepository;
    final AmendmentSectorMemberRepository memberRepository;
    final AmendmentPendencyRepository pendencyRepository;
    final UserRepository userRepository;
    final AmendmentTimeline timeline;
    final AmendmentMapper mapper;
    final AmendmentAccessService access;

    public AmendmentService(AmendmentRepository amendmentRepository, AmendmentSectorRepository sectorRepository,
            AmendmentSectorMemberRepository memberRepository, AmendmentPendencyRepository pendencyRepository,
            UserRepository userRepository, AmendmentTimeline timeline, AmendmentMapper mapper,
            AmendmentAccessService access) {
        this.amendmentRepository = amendmentRepository;
        this.sectorRepository = sectorRepository;
        this.memberRepository = memberRepository;
        this.pendencyRepository = pendencyRepository;
        this.userRepository = userRepository;
        this.timeline = timeline;
        this.mapper = mapper;
        this.access = access;
    }

    Amendment getAmendmentById(UUID id) {
        return amendmentRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Emenda não encontrada: " + id));
    }

    // Busca a emenda e confere se ela está no escopo do usuário (403 se for de outro setor)
    Amendment getAmendmentInScope(UUID id) {
        Amendment amendment = getAmendmentById(id);
        access.requireInScope(amendment);
        return amendment;
    }

    AmendmentResponse toResponse(Amendment amendment) {
        return mapper.amendment(amendment,
            pendencyRepository.findByAmendmentIdAndResolvedAtIsNullOrderByOpenedAtAsc(amendment.getId()));
    }

    // ---------------------------------------------------------------- consultas

    @Transactional(readOnly = true)
    public List<AmendmentResponse> search(AmendmentFilter filter, AmendmentSort sort) {
        access.requireRead();
        UUID currentUserId = Boolean.TRUE.equals(filter.mine()) ? access.requireUser().getId() : null;
        List<Amendment> amendments = amendmentRepository.findAll(spec(filter, currentUserId, access.scope(), true));
        if (amendments.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<AmendmentPendency>> open = pendencyRepository
            .findOpenByAmendmentIds(amendments.stream().map(Amendment::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(p -> p.getAmendment().getId()));
        return amendments.stream()
            .map(amendment -> mapper.amendment(amendment, open.getOrDefault(amendment.getId(), List.of())))
            .sorted(comparator(sort != null ? sort : AmendmentSort.RECENT))
            .toList();
    }

    @Transactional(readOnly = true)
    public AmendmentResponse findById(UUID id) {
        access.requireRead();
        return toResponse(getAmendmentInScope(id));
    }

    // Contadores para o painel. Os filtros de escopo (ano, setor, responsável) valem para todos os números
    @Transactional(readOnly = true)
    public AmendmentSummaryResponse summary(Integer exerciseYear, UUID sectorId, UUID responsibleUserId, Boolean mine,
            Integer idleDays) {
        access.requireRead();
        int idle = idleDays != null && idleDays > 0 ? idleDays : DEFAULT_IDLE_DAYS;
        UUID responsible = Boolean.TRUE.equals(mine) ? access.requireUser().getId() : responsibleUserId;
        AmendmentScope scope = access.scope();

        Map<AmendmentStatus, Long> byStatus = new EnumMap<>(AmendmentStatus.class);
        for (AmendmentStatus status : AmendmentStatus.values()) {
            byStatus.put(status, count(scope, scope(exerciseYear, sectorId, responsible, List.of(status), null, null, null, null)));
        }
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long closed = byStatus.get(AmendmentStatus.COMPLETED) + byStatus.get(AmendmentStatus.CANCELLED);
        long pending = byStatus.get(AmendmentStatus.WAITING) + byStatus.get(AmendmentStatus.BLOCKED);
        return new AmendmentSummaryResponse(
            total,
            byStatus,
            total - closed,
            pending,
            count(scope, scope(exerciseYear, sectorId, responsible, null, null, null, true, null)),
            count(scope, scope(exerciseYear, sectorId, responsible, null, null, mapper.deadlineAlertDays(), null, null)),
            mapper.deadlineAlertDays(),
            count(scope, scope(exerciseYear, sectorId, responsible, null, idle, null, null, null)),
            idle
        );
    }

    @Transactional(readOnly = true)
    public AmendmentOptionsResponse options() {
        access.requireRead();
        return new AmendmentOptionsResponse(
            amendmentRepository.findExerciseYears(),
            amendmentRepository.findCycles(),
            amendmentRepository.findParliamentarians(),
            amendmentRepository.findMunicipalities(),
            amendmentRepository.findCategories(),
            amendmentRepository.findTypes(),
            amendmentRepository.findTechnicalAreas()
        );
    }

    private long count(AmendmentScope scope, AmendmentFilter filter) {
        return amendmentRepository.count(spec(filter, null, scope, false));
    }

    private static AmendmentFilter scope(Integer exerciseYear, UUID sectorId, UUID responsibleUserId,
            List<AmendmentStatus> status, Integer idleDays, Integer deadlineWithinDays, Boolean expired, Boolean pending) {
        return new AmendmentFilter(status, null, pending, null, idleDays, deadlineWithinDays, expired, sectorId,
            responsibleUserId, null, exerciseYear, null, null, null, null);
    }

    // ---------------------------------------------------------------- cadastro

    @Transactional
    public AmendmentResponse create(AmendmentRequest request) {
        User user = access.requireCreate();
        AmendmentScope scope = access.scope();
        String code = AmendmentMapper.normalize(request.code());
        requireUniqueCode(request.exerciseYear(), code, null);

        LocalDateTime now = LocalDateTime.now();
        Amendment amendment = Amendment.builder()
            .status(AmendmentStatus.IN_PROGRESS)
            .lastActivityAt(now)
            .createdBy(user)
            .build();
        apply(amendment, request, code);
        if (scope.restricted() && amendment.getSector() == null) {
            amendment.setSector(scope.sector());
        }
        requireSectorRules(scope, amendment, null);
        amendmentRepository.saveAndFlush(amendment);
        timeline.record(amendment, AmendmentActivityKind.CREATED, "Emenda cadastrada", null, null, user, now);
        return toResponse(amendment);
    }

    // O corpo substitui os valores atuais. Só os campos que mudaram entram na linha do tempo
    @Transactional
    public AmendmentResponse update(UUID id, AmendmentRequest request) {
        User user = access.requireUpdate();
        Amendment amendment = amendmentRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new NotFoundException("Emenda não encontrada: " + id));
        AmendmentScope scope = access.scope();
        AmendmentAccessService.requireInScope(scope, amendment);
        if (amendment.getStatus().isClosed()) {
            throw new BusinessException("A emenda está " + amendment.getStatus().getLabel().toLowerCase(Locale.ROOT)
                + " e não pode ser editada. Reabra-a antes");
        }
        String code = AmendmentMapper.normalize(request.code());
        requireUniqueCode(request.exerciseYear(), code, id);
        UUID previousSectorId = amendment.getSector() != null ? amendment.getSector().getId() : null;

        Map<String, Object> before = snapshot(amendment);
        apply(amendment, request, code);
        requireSectorRules(scope, amendment, previousSectorId);
        Map<String, Object> after = snapshot(amendment);
        Map<String, Object> changes = new LinkedHashMap<>();
        before.forEach((field, value) -> ActivityService.trackChange(changes, field, value, after.get(field)));

        String comment = AmendmentMapper.normalize(request.comment());
        LocalDateTime now = LocalDateTime.now();
        if (!changes.isEmpty()) {
            timeline.record(amendment, AmendmentActivityKind.FIELD_UPDATE, comment, changes, null, user, now);
        } else if (comment != null) {
            timeline.record(amendment, AmendmentActivityKind.NOTE, comment, null, null, user, now);
        }
        amendmentRepository.saveAndFlush(amendment);
        return toResponse(amendment);
    }

    // Exclui a emenda com pendências e linha do tempo (em cascata)
    @Transactional
    public void delete(UUID id) {
        access.requireDelete();
        amendmentRepository.delete(getAmendmentInScope(id));
    }

    // Técnica com setor não põe emenda em outro setor; a responsável, se tiver setor, é do setor da emenda
    private void requireSectorRules(AmendmentScope scope, Amendment amendment, UUID previousSectorId) {
        UUID sectorId = amendment.getSector() != null ? amendment.getSector().getId() : null;
        if (scope.restricted() && !Objects.equals(sectorId, previousSectorId)
                && !Objects.equals(sectorId, scope.sectorId())) {
            throw new ForbiddenException("Você só pode colocar emendas no seu setor (" + scope.sector().getName() + ")");
        }
        User responsible = amendment.getResponsibleUser();
        if (responsible == null) {
            return;
        }
        memberRepository.findByUserId(responsible.getId())
            .map(AmendmentSectorMember::getSector)
            .filter(memberSector -> !memberSector.getId().equals(sectorId))
            .ifPresent(memberSector -> {
                throw new BusinessException(responsible.getName() + " é técnica do setor " + memberSector.getName()
                    + (amendment.getSector() != null ? " e a emenda está no setor " + amendment.getSector().getName()
                        : " e a emenda está sem setor"));
            });
    }

    private void requireUniqueCode(Integer exerciseYear, String code, UUID currentId) {
        if (code == null) {
            return;
        }
        amendmentRepository.findByYearAndCode(exerciseYear, code)
            .filter(existing -> !existing.getId().equals(currentId))
            .ifPresent(existing -> {
                throw new ConflictException("Já existe uma emenda com o código " + code + " em " + exerciseYear);
            });
    }

    private void apply(Amendment a, AmendmentRequest r, String code) {
        a.setExerciseYear(r.exerciseYear());
        a.setCycle(AmendmentMapper.normalize(r.cycle()));
        a.setParliamentarian(r.parliamentarian().trim());
        a.setCategory(AmendmentMapper.normalize(r.category()));
        a.setAlepeCode(AmendmentMapper.normalize(r.alepeCode()));
        a.setCode(code);
        a.setActionCode(AmendmentMapper.normalize(r.actionCode()));
        a.setActionName(AmendmentMapper.normalize(r.actionName()));
        a.setSubAction(AmendmentMapper.normalize(r.subAction()));
        a.setExpenseGroup(AmendmentMapper.normalize(r.expenseGroup()));
        a.setApplicationModality(AmendmentMapper.normalize(r.applicationModality()));
        a.setBeneficiary(AmendmentMapper.normalize(r.beneficiary()));
        a.setMunicipality(AmendmentMapper.normalize(r.municipality()));
        a.setObject(AmendmentMapper.normalize(r.object()));
        a.setType(AmendmentMapper.normalize(r.type()));
        a.setUnderChange(AmendmentMapper.normalize(r.underChange()));
        a.setCurrentAmount(r.currentAmount() != null ? r.currentAmount().setScale(2, RoundingMode.HALF_UP) : null);
        a.setFinancialStatus(AmendmentMapper.normalize(r.financialStatus()));
        a.setSeplagImpediment(AmendmentMapper.normalize(r.seplagImpediment()));
        a.setDocumentalStatus(AmendmentMapper.normalize(r.documentalStatus()));
        a.setDetails(AmendmentMapper.normalize(r.details()));
        a.setNotes(AmendmentMapper.normalize(r.notes()));
        a.setTechnicalArea(AmendmentMapper.normalize(r.technicalArea()));
        a.setSector(resolveSector(a.getSector(), r.sectorId()));
        User responsible = r.responsibleUserId() != null
            ? userRepository.findById(r.responsibleUserId())
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado: " + r.responsibleUserId()))
            : null;
        a.setResponsibleUser(responsible);
        String responsibleName = AmendmentMapper.normalize(r.responsibleName());
        a.setResponsibleName(responsibleName == null && responsible != null ? responsible.getName() : responsibleName);
        a.setSeiNumber(AmendmentMapper.normalize(r.seiNumber()));
        a.setTechnicalOpinion(AmendmentMapper.normalize(r.technicalOpinion()));
        a.setTermNumber(AmendmentMapper.normalize(r.termNumber()));
        a.setTermEndDate(r.termEndDate());
        a.setPfNumber(AmendmentMapper.normalize(r.pfNumber()));
        a.setPfDate(r.pfDate());
        a.setSefazPf(AmendmentMapper.normalize(r.sefazPf()));
        a.setCommitmentNumber(AmendmentMapper.normalize(r.commitmentNumber()));
        a.setDhNumber(AmendmentMapper.normalize(r.dhNumber()));
        a.setLeNumber(AmendmentMapper.normalize(r.leNumber()));
        a.setPd(AmendmentMapper.normalize(r.pd()));
        a.setObNumber(AmendmentMapper.normalize(r.obNumber()));
        a.setPaymentDate(r.paymentDate());
        a.setCallNumber(AmendmentMapper.normalize(r.callNumber()));
        a.setProposalNumber(AmendmentMapper.normalize(r.proposalNumber()));
        a.setSituation(AmendmentMapper.normalize(r.situation()));
    }

    // Manter o setor atual é sempre permitido (mesmo inativo); trocar exige setor ativo
    private AmendmentSector resolveSector(AmendmentSector current, UUID sectorId) {
        if (sectorId == null) {
            return null;
        }
        if (current != null && current.getId().equals(sectorId)) {
            return current;
        }
        AmendmentSector sector = sectorRepository.findById(sectorId)
            .orElseThrow(() -> new NotFoundException("Setor não encontrado: " + sectorId));
        if (!Boolean.TRUE.equals(sector.getIsActive())) {
            throw new BusinessException("O setor " + sector.getName() + " está inativo");
        }
        return sector;
    }

    // Valores comparáveis de cada campo (nomes da API), para registrar o que mudou
    static Map<String, Object> snapshot(Amendment a) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("exerciseYear", a.getExerciseYear());
        values.put("cycle", a.getCycle());
        values.put("parliamentarian", a.getParliamentarian());
        values.put("category", a.getCategory());
        values.put("alepeCode", a.getAlepeCode());
        values.put("code", a.getCode());
        values.put("actionCode", a.getActionCode());
        values.put("actionName", a.getActionName());
        values.put("subAction", a.getSubAction());
        values.put("expenseGroup", a.getExpenseGroup());
        values.put("applicationModality", a.getApplicationModality());
        values.put("beneficiary", a.getBeneficiary());
        values.put("municipality", a.getMunicipality());
        values.put("object", a.getObject());
        values.put("type", a.getType());
        values.put("underChange", a.getUnderChange());
        values.put("currentAmount", a.getCurrentAmount() != null ? a.getCurrentAmount().setScale(2, RoundingMode.HALF_UP) : null);
        values.put("financialStatus", a.getFinancialStatus());
        values.put("seplagImpediment", a.getSeplagImpediment());
        values.put("documentalStatus", a.getDocumentalStatus());
        values.put("details", a.getDetails());
        values.put("notes", a.getNotes());
        values.put("technicalArea", a.getTechnicalArea());
        values.put("sector", a.getSector() != null ? a.getSector().getName() : null);
        values.put("responsibleUser", a.getResponsibleUser() != null ? a.getResponsibleUser().getName() : null);
        values.put("responsibleName", a.getResponsibleName());
        values.put("seiNumber", a.getSeiNumber());
        values.put("technicalOpinion", a.getTechnicalOpinion());
        values.put("termNumber", a.getTermNumber());
        values.put("termEndDate", a.getTermEndDate());
        values.put("pfNumber", a.getPfNumber());
        values.put("pfDate", a.getPfDate());
        values.put("sefazPf", a.getSefazPf());
        values.put("commitmentNumber", a.getCommitmentNumber());
        values.put("dhNumber", a.getDhNumber());
        values.put("leNumber", a.getLeNumber());
        values.put("pd", a.getPd());
        values.put("obNumber", a.getObNumber());
        values.put("paymentDate", a.getPaymentDate());
        values.put("callNumber", a.getCallNumber());
        values.put("proposalNumber", a.getProposalNumber());
        values.put("situation", a.getSituation());
        return values;
    }

    // ---------------------------------------------------------------- filtros e ordenação

    // idleDays, deadlineWithinDays e expired só olham emendas abertas (concluída/cancelada não está parada
    // nem atrasada). fetch = false nas contagens.
    private static Specification<Amendment> spec(AmendmentFilter f, UUID currentUserId, AmendmentScope scope,
            boolean fetch) {
        return (root, query, cb) -> {
            if (fetch && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("sector", JoinType.LEFT);
                root.fetch("responsibleUser", JoinType.LEFT);
                root.fetch("completedBy", JoinType.LEFT);
                root.fetch("cancelledBy", JoinType.LEFT);
            }
            LocalDate today = LocalDate.now();
            List<Predicate> predicates = new ArrayList<>();
            Expression<AmendmentStatus> status = root.get("status");

            // Técnica com setor: emendas do setor ou sob sua responsabilidade
            if (scope != null && scope.restricted()) {
                predicates.add(cb.or(
                    cb.equal(root.get("sector").get("id"), scope.sectorId()),
                    cb.equal(root.get("responsibleUser").get("id"), scope.userId())));
            }
            if (f.status() != null && !f.status().isEmpty()) {
                predicates.add(status.in(f.status()));
            }
            if (f.open() != null) {
                predicates.add(f.open() ? cb.not(status.in(CLOSED)) : status.in(CLOSED));
            }
            if (f.pending() != null) {
                predicates.add(f.pending() ? status.in(PENDING) : cb.not(status.in(PENDING)));
            }
            if (f.pendencyKind() != null) {
                predicates.add(cb.exists(openPendency(root, query.subquery(UUID.class), cb, f.pendencyKind())));
            }
            if (f.idleDays() != null) {
                // Parada há pelo menos N dias: última atividade antes do início de (hoje - N + 1)
                LocalDateTime limit = today.minusDays(Math.max(f.idleDays(), 0) - 1L).atStartOfDay();
                predicates.add(cb.lessThan(root.get("lastActivityAt"), limit));
                predicates.add(cb.not(status.in(CLOSED)));
            }
            if (f.deadlineWithinDays() != null) {
                predicates.add(cb.between(root.get("termEndDate"), today, today.plusDays(Math.max(f.deadlineWithinDays(), 0))));
                predicates.add(cb.not(status.in(CLOSED)));
            }
            if (f.expired() != null) {
                Predicate expired = cb.and(cb.isNotNull(root.get("termEndDate")),
                    cb.lessThan(root.get("termEndDate"), today), cb.not(status.in(CLOSED)));
                predicates.add(f.expired() ? expired : cb.not(expired));
            }
            if (f.sectorId() != null) {
                predicates.add(cb.equal(root.get("sector").get("id"), f.sectorId()));
            }
            UUID responsible = currentUserId != null ? currentUserId : f.responsibleUserId();
            if (responsible != null) {
                predicates.add(cb.equal(root.get("responsibleUser").get("id"), responsible));
            }
            if (f.exerciseYear() != null) {
                predicates.add(cb.equal(root.get("exerciseYear"), f.exerciseYear()));
            }
            String cycle = AmendmentMapper.normalize(f.cycle());
            if (cycle != null) {
                predicates.add(cb.equal(cb.lower(root.get("cycle")), cycle.toLowerCase(Locale.ROOT)));
            }
            addContains(predicates, cb, List.of(root.get("parliamentarian")), f.parliamentarian());
            addContains(predicates, cb, List.of(root.get("municipality")), f.municipality());
            addContains(predicates, cb, List.of(root.get("parliamentarian"), root.get("beneficiary"),
                root.get("municipality"), root.get("object"), root.get("code"), root.get("alepeCode"),
                root.get("seiNumber"), root.get("termNumber"), root.get("responsibleName"),
                root.get("actionName")), f.q());
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Subquery<UUID> openPendency(Root<Amendment> root, Subquery<UUID> subquery, CriteriaBuilder cb,
            PendencyKind kind) {
        Root<AmendmentPendency> pendency = subquery.from(AmendmentPendency.class);
        return subquery.select(pendency.get("id")).where(
            cb.equal(pendency.get("amendment"), root),
            cb.equal(pendency.get("kind"), kind),
            cb.isNull(pendency.get("resolvedAt")));
    }

    // Busca "contém", sem diferenciar maiúsculas, em qualquer um dos campos
    private static void addContains(List<Predicate> predicates, CriteriaBuilder cb, List<Expression<String>> fields,
            String term) {
        String value = AmendmentMapper.normalize(term);
        if (value == null) {
            return;
        }
        String pattern = "%" + value.toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        predicates.add(cb.or(fields.stream()
            .map(field -> cb.like(cb.lower(field), pattern, '\\'))
            .toArray(Predicate[]::new)));
    }

    private static Comparator<AmendmentResponse> comparator(AmendmentSort sort) {
        Comparator<AmendmentResponse> byRecent = Comparator.comparing(AmendmentResponse::lastActivityAt,
            Comparator.reverseOrder());
        return switch (sort) {
            case RECENT -> byRecent;
            case IDLE -> Comparator.comparing(AmendmentResponse::lastActivityAt);
            case DEADLINE -> Comparator.comparing(AmendmentResponse::termEndDate,
                Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(byRecent);
            case OLDEST_PENDENCY -> Comparator.comparing(AmendmentResponse::oldestOpenPendencyAt,
                Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(byRecent);
            case AMOUNT -> Comparator.comparing(AmendmentResponse::currentAmount,
                Comparator.nullsLast(Comparator.<BigDecimal>reverseOrder())).thenComparing(byRecent);
            case PARLIAMENTARIAN -> Comparator.comparing((AmendmentResponse r) -> r.parliamentarian().toLowerCase(Locale.ROOT))
                .thenComparing(r -> Objects.requireNonNullElse(r.code(), ""));
        };
    }
}
