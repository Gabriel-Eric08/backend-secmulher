package com.secmulher.modules.finance.domain.services;

import static com.secmulher.modules.finance.domain.services.FinanceMapper.ZERO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.modules.core.domain.exceptions.NotFoundException;
import com.secmulher.modules.finance.domain.access.FinanceAccessService;
import com.secmulher.modules.finance.domain.dtos.FinanceActionDtos.FinanceActionResponse;
import com.secmulher.modules.finance.domain.dtos.FinanceDashboardDtos.DashboardResponse;
import com.secmulher.modules.finance.domain.dtos.FinanceOperationDtos.ActionHistoryItem;
import com.secmulher.modules.finance.domain.dtos.FinanceOperationDtos.OperationResponse;
import com.secmulher.modules.finance.domain.dtos.FinanceOperationDtos.SubActionHistoryItem;
import com.secmulher.modules.finance.domain.dtos.FinanceSubActionDtos.FinanceSubActionResponse;
import com.secmulher.modules.finance.domain.services.FinanceMapper.SubValues;
import com.secmulher.modules.finance.infra.entities.FinanceAction;
import com.secmulher.modules.finance.infra.entities.FinanceEntry;
import com.secmulher.modules.finance.infra.entities.FinanceOperation;
import com.secmulher.modules.finance.infra.entities.FinanceSubAction;
import com.secmulher.modules.finance.infra.entities.enums.FinanceEntryKind;
import com.secmulher.modules.finance.infra.repositories.FinanceActionRepository;
import com.secmulher.modules.finance.infra.repositories.FinanceBalanceRow;
import com.secmulher.modules.finance.infra.repositories.FinanceEntryRepository;
import com.secmulher.modules.finance.infra.repositories.FinanceSubActionRepository;

// Leituras: dashboard, valores em uma data e históricos.
// Sem data, os valores são os gravados na ação/subação; com data, são a soma dos lançamentos
// até o fim daquele dia, considerando só as ações e subações que já existiam.
@Service
public class FinanceQueryService {
    final FinanceActionRepository actionRepository;
    final FinanceSubActionRepository subActionRepository;
    final FinanceEntryRepository entryRepository;
    final FinanceAccessService access;
    final FinanceMapper mapper;

    public FinanceQueryService(FinanceActionRepository actionRepository, FinanceSubActionRepository subActionRepository,
            FinanceEntryRepository entryRepository, FinanceAccessService access, FinanceMapper mapper) {
        this.actionRepository = actionRepository;
        this.subActionRepository = subActionRepository;
        this.entryRepository = entryRepository;
        this.access = access;
        this.mapper = mapper;
    }

    // Soma acumulada de cada tipo de lançamento
    private static final class Totals {
        final Map<FinanceEntryKind, BigDecimal> values = new EnumMap<>(FinanceEntryKind.class);

        BigDecimal get(FinanceEntryKind kind) {
            return values.getOrDefault(kind, ZERO);
        }

        void add(FinanceEntryKind kind, BigDecimal amount) {
            values.merge(kind, amount, BigDecimal::add);
        }

        void addAll(Totals other) {
            other.values.forEach(this::add);
        }

        SubValues asSubValues() {
            return new SubValues(get(FinanceEntryKind.BUDGET), get(FinanceEntryKind.COMMITTED),
                get(FinanceEntryKind.LIQUIDATED), get(FinanceEntryKind.PAID));
        }
    }

    // Valores de cada ação (total) e subação (dotação e execução) no fim do dia informado
    // Uma ação/subação aparece na data se foi cadastrada até lá ou se já tem operações com data real até lá
    private record Snapshot(LocalDateTime cutoff, Map<UUID, BigDecimal> actionTotals, Map<UUID, Totals> subTotals,
            Set<UUID> actionsWithOperations) {
        static Snapshot at(LocalDateTime cutoff, List<FinanceBalanceRow> rows) {
            Map<UUID, BigDecimal> actionTotals = new HashMap<>();
            Map<UUID, Totals> subTotals = new HashMap<>();
            Set<UUID> actionsWithOperations = new HashSet<>();
            for (FinanceBalanceRow row : rows) {
                actionsWithOperations.add(row.actionId());
                if (row.subActionId() == null) {
                    actionTotals.merge(row.actionId(), row.amount(), BigDecimal::add);
                } else {
                    subTotals.computeIfAbsent(row.subActionId(), id -> new Totals()).add(row.kind(), row.amount());
                }
            }
            return new Snapshot(cutoff, actionTotals, subTotals, actionsWithOperations);
        }

        private boolean createdBefore(LocalDateTime createdAt) {
            return createdAt != null && createdAt.isBefore(cutoff);
        }

        boolean existed(FinanceAction action) {
            return actionsWithOperations.contains(action.getId()) || createdBefore(action.getCreatedAt());
        }

        boolean existed(FinanceSubAction sub) {
            return subTotals.containsKey(sub.getId()) || createdBefore(sub.getCreatedAt());
        }

        BigDecimal total(FinanceAction action) {
            return actionTotals.getOrDefault(action.getId(), ZERO);
        }

        SubValues values(FinanceSubAction sub) {
            Totals totals = subTotals.get(sub.getId());
            return totals != null ? totals.asSubValues() : SubValues.EMPTY;
        }
    }

    private Snapshot snapshot(LocalDate date) {
        return Snapshot.at(date.plusDays(1).atStartOfDay(), entryRepository.sumBalancesUntil(date));
    }

    private FinanceAction getActionById(UUID id) {
        return actionRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Ação não encontrada: " + id));
    }

    private FinanceSubAction getSubActionById(UUID id) {
        return subActionRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Subação não encontrada: " + id));
    }

    private static NotFoundException notYetCreated(String what, String code, LocalDate date) {
        return new NotFoundException(what + " " + code + " ainda não existia em " + date);
    }

    // ---------------------------------------------------------------- dashboard e saldos

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(LocalDate date) {
        access.requireRead();
        List<FinanceAction> actions = actionRepository.findAllByOrderByCodeAsc();
        Map<UUID, List<FinanceSubAction>> subsByAction = subActionRepository.findAllByOrderByCodeAsc().stream()
            .collect(Collectors.groupingBy(sub -> sub.getAction().getId(), LinkedHashMap::new, Collectors.toList()));

        List<FinanceActionResponse> responses;
        if (date == null) {
            responses = actions.stream()
                .map(action -> mapper.currentAction(action, subsByAction.getOrDefault(action.getId(), List.of())))
                .toList();
        } else {
            Snapshot snapshot = snapshot(date);
            responses = actions.stream()
                .filter(action -> snapshot.existed(action))
                .map(action -> mapper.action(action,
                    subsByAction.getOrDefault(action.getId(), List.of()).stream()
                        .filter(sub -> snapshot.existed(sub)).toList(),
                    snapshot.total(action), snapshot::values))
                .toList();
        }

        return new DashboardResponse(date,
            FinanceMapper.sum(responses, FinanceActionResponse::totalAmount),
            FinanceMapper.sum(responses, FinanceActionResponse::distributedAmount),
            FinanceMapper.sum(responses, FinanceActionResponse::undistributedAmount),
            FinanceMapper.sum(responses, FinanceActionResponse::committedAmount),
            FinanceMapper.sum(responses, FinanceActionResponse::liquidatedAmount),
            FinanceMapper.sum(responses, FinanceActionResponse::paidAmount),
            FinanceMapper.sum(responses, FinanceActionResponse::availableAmount),
            responses);
    }

    @Transactional(readOnly = true)
    public FinanceActionResponse action(UUID id, LocalDate date) {
        access.requireRead();
        FinanceAction action = getActionById(id);
        List<FinanceSubAction> subs = subActionRepository.findByActionIdOrderByCodeAsc(id);
        if (date == null) {
            return mapper.currentAction(action, subs);
        }
        Snapshot snapshot = snapshot(date);
        if (!snapshot.existed(action)) {
            throw notYetCreated("A ação", action.getCode(), date);
        }
        return mapper.action(action, subs.stream().filter(sub -> snapshot.existed(sub)).toList(),
            snapshot.total(action), snapshot::values);
    }

    @Transactional(readOnly = true)
    public FinanceSubActionResponse subAction(UUID id, LocalDate date) {
        access.requireRead();
        FinanceSubAction sub = getSubActionById(id);
        if (date == null) {
            return mapper.currentSubAction(sub);
        }
        Snapshot snapshot = snapshot(date);
        if (!snapshot.existed(sub)) {
            throw notYetCreated("A subação", sub.getCode(), date);
        }
        return mapper.subAction(sub, sub.getAction(), snapshot.values(sub));
    }

    // ---------------------------------------------------------------- históricos (mais recente primeiro)

    // Uma linha do histórico antes de virar resposta: a operação, o que ela mudou e como tudo ficou
    private record Step(FinanceOperation operation, Totals delta, Totals after, BigDecimal totalDelta, BigDecimal totalAfter) {}

    // Agrupa os lançamentos (já em ordem cronológica) por operação e acumula os valores.
    // Lançamentos sem subação (valor total da ação) somam em totalDelta; os demais, por tipo.
    private static List<Step> accumulate(List<FinanceEntry> entries) {
        Map<FinanceOperation, List<FinanceEntry>> byOperation = new LinkedHashMap<>();
        entries.forEach(entry -> byOperation.computeIfAbsent(entry.getOperation(), key -> new ArrayList<>()).add(entry));

        List<Step> steps = new ArrayList<>();
        Totals running = new Totals();
        BigDecimal total = ZERO;
        for (Map.Entry<FinanceOperation, List<FinanceEntry>> group : byOperation.entrySet()) {
            Totals delta = new Totals();
            BigDecimal totalDelta = ZERO;
            for (FinanceEntry entry : group.getValue()) {
                if (entry.getSubAction() == null) {
                    totalDelta = totalDelta.add(entry.getAmount());
                } else {
                    delta.add(entry.getKind(), entry.getAmount());
                }
            }
            running.addAll(delta);
            total = total.add(totalDelta);
            Totals after = new Totals();
            after.addAll(running);
            steps.add(new Step(group.getKey(), delta, after, totalDelta, total));
        }
        return steps;
    }

    @Transactional(readOnly = true)
    public List<ActionHistoryItem> actionHistory(UUID id, LocalDate from, LocalDate to) {
        access.requireRead();
        getActionById(id);
        return inPeriod(accumulate(entryRepository.findByActionIdInOrder(id)), from, to, (operation, step) -> {
            BigDecimal distributedDelta = step.delta().get(FinanceEntryKind.BUDGET);
            BigDecimal distributedAfter = step.after().get(FinanceEntryKind.BUDGET);
            BigDecimal committedAfter = step.after().get(FinanceEntryKind.COMMITTED);
            return new ActionHistoryItem(operation,
                step.totalDelta(), step.totalAfter(),
                step.totalDelta().subtract(distributedDelta), step.totalAfter().subtract(distributedAfter),
                step.delta().get(FinanceEntryKind.COMMITTED), committedAfter,
                step.delta().get(FinanceEntryKind.LIQUIDATED), step.after().get(FinanceEntryKind.LIQUIDATED),
                step.delta().get(FinanceEntryKind.PAID), step.after().get(FinanceEntryKind.PAID),
                step.totalAfter().subtract(committedAfter));
        });
    }

    @Transactional(readOnly = true)
    public List<SubActionHistoryItem> subActionHistory(UUID id, LocalDate from, LocalDate to) {
        access.requireRead();
        getSubActionById(id);
        return inPeriod(accumulate(entryRepository.findBySubActionIdInOrder(id)), from, to, (operation, step) -> {
            BigDecimal balanceAfter = step.after().get(FinanceEntryKind.BUDGET);
            BigDecimal committedAfter = step.after().get(FinanceEntryKind.COMMITTED);
            return new SubActionHistoryItem(operation,
                step.delta().get(FinanceEntryKind.BUDGET), balanceAfter,
                step.delta().get(FinanceEntryKind.COMMITTED), committedAfter,
                step.delta().get(FinanceEntryKind.LIQUIDATED), step.after().get(FinanceEntryKind.LIQUIDATED),
                step.delta().get(FinanceEntryKind.PAID), step.after().get(FinanceEntryKind.PAID),
                balanceAfter.subtract(committedAfter));
        });
    }

    // Os valores são acumulados desde o início; o período só filtra o que é devolvido
    private <T> List<T> inPeriod(List<Step> steps, LocalDate from, LocalDate to,
            BiFunction<OperationResponse, Step, T> build) {
        List<Step> selected = steps.stream()
            .filter(step -> from == null || !step.operation().getOperationDate().isBefore(from))
            .filter(step -> to == null || !step.operation().getOperationDate().isAfter(to))
            .toList();
        Map<UUID, OperationResponse> responses = mapper.operations(selected.stream().map(Step::operation).toList())
            .stream().collect(Collectors.toMap(OperationResponse::id, Function.identity()));

        List<T> items = new ArrayList<>(selected.stream()
            .map(step -> build.apply(responses.get(step.operation().getId()), step))
            .toList());
        Collections.reverse(items);
        return items;
    }
}
