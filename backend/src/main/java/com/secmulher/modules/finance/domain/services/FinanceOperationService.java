package com.secmulher.modules.finance.domain.services;

import static com.secmulher.modules.finance.domain.services.FinanceMapper.money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.modules.core.domain.exceptions.BusinessException;
import com.secmulher.modules.core.domain.exceptions.FieldValidationException;
import com.secmulher.modules.core.domain.exceptions.NotFoundException;
import com.secmulher.modules.core.infra.entities.User;
import com.secmulher.modules.finance.domain.access.FinanceAccessService;
import com.secmulher.modules.finance.domain.dtos.FinanceOperationDtos.AllocationRequest;
import com.secmulher.modules.finance.domain.dtos.FinanceOperationDtos.DistributionRequest;
import com.secmulher.modules.finance.domain.dtos.FinanceOperationDtos.OperationResponse;
import com.secmulher.modules.finance.domain.dtos.FinanceOperationDtos.TacRequest;
import com.secmulher.modules.finance.domain.dtos.FinanceOperationDtos.TransferRequest;
import com.secmulher.modules.finance.infra.entities.FinanceAction;
import com.secmulher.modules.finance.infra.entities.FinanceCommitment;
import com.secmulher.modules.finance.infra.entities.FinanceEntry;
import com.secmulher.modules.finance.infra.entities.FinanceOperation;
import com.secmulher.modules.finance.infra.entities.FinanceSubAction;
import com.secmulher.modules.finance.infra.entities.enums.FinanceEntryKind;
import com.secmulher.modules.finance.infra.entities.enums.FinanceOperationMethod;
import com.secmulher.modules.finance.infra.repositories.FinanceActionRepository;
import com.secmulher.modules.finance.infra.repositories.FinanceEntryRepository;
import com.secmulher.modules.finance.infra.repositories.FinanceOperationRepository;
import com.secmulher.modules.finance.infra.repositories.FinanceSubActionRepository;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

// Operações que movimentam valores. Cada operação grava uma linha em finance_operations e um
// lançamento com sinal em finance_entries para cada valor que muda; os saldos gravados na ação e nas
// subações são atualizados na mesma transação, com as ações envolvidas travadas.
@Service
public class FinanceOperationService {
    // Mais recente primeiro pela data real; no mesmo dia, pela ordem de registro
    static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("operationDate"), Sort.Order.desc("number"));

    final FinanceActionRepository actionRepository;
    final FinanceSubActionRepository subActionRepository;
    final FinanceOperationRepository operationRepository;
    final FinanceEntryRepository entryRepository;
    final FinanceAccessService access;
    final FinanceMapper mapper;

    public FinanceOperationService(FinanceActionRepository actionRepository,
            FinanceSubActionRepository subActionRepository, FinanceOperationRepository operationRepository,
            FinanceEntryRepository entryRepository, FinanceAccessService access, FinanceMapper mapper) {
        this.actionRepository = actionRepository;
        this.subActionRepository = subActionRepository;
        this.operationRepository = operationRepository;
        this.entryRepository = entryRepository;
        this.access = access;
        this.mapper = mapper;
    }

    // ---------------------------------------------------------------- consultas

    @Transactional(readOnly = true)
    public List<OperationResponse> findAll(UUID actionId, UUID subActionId, UUID commitmentId,
            FinanceOperationMethod method, LocalDate from, LocalDate to) {
        access.requireRead();
        Specification<FinanceOperation> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (method != null) {
                predicates.add(cb.equal(root.get("method"), method));
            }
            if (commitmentId != null) {
                predicates.add(cb.equal(root.get("commitment").get("id"), commitmentId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("operationDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("operationDate"), to));
            }
            if (actionId != null || subActionId != null) {
                Subquery<UUID> entries = query.subquery(UUID.class);
                Root<FinanceEntry> entry = entries.from(FinanceEntry.class);
                List<Predicate> entryPredicates = new ArrayList<>();
                entryPredicates.add(cb.equal(entry.get("operation"), root));
                if (actionId != null) {
                    entryPredicates.add(cb.equal(entry.get("action").get("id"), actionId));
                }
                if (subActionId != null) {
                    entryPredicates.add(cb.equal(entry.get("subAction").get("id"), subActionId));
                }
                entries.select(entry.get("id")).where(entryPredicates.toArray(Predicate[]::new));
                predicates.add(cb.exists(entries));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return mapper.operations(operationRepository.findAll(spec, NEWEST_FIRST));
    }

    @Transactional(readOnly = true)
    public OperationResponse findById(UUID id) {
        access.requireRead();
        return mapper.operation(operationRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Operação não encontrada: " + id)));
    }

    // ---------------------------------------------------------------- operações

    // Método A: move saldo entre subações da mesma ação; o total da ação não muda
    @Transactional
    public OperationResponse transferBetweenSubActions(TransferRequest request) {
        User user = access.requireUpdate();
        requireDifferentSubActions(request);
        UUID fromActionId = actionIdOf(request.fromSubActionId());
        UUID toActionId = actionIdOf(request.toSubActionId());
        if (!fromActionId.equals(toActionId)) {
            throw new BusinessException("As subações pertencem a ações diferentes; use o Método B (transferência entre ações)");
        }

        FinanceAction action = lockActions(fromActionId).get(fromActionId);
        requireActive(action);
        FinanceSubAction from = subActionOf(request.fromSubActionId(), action);
        FinanceSubAction to = subActionOf(request.toSubActionId(), action);
        BigDecimal amount = request.amount().setScale(2);
        LocalDate date = dateOrToday(request.date());
        requireBalance(from, amount);
        checkSubActionOutflow(from, date, amount);

        FinanceOperation operation = newOperation(FinanceOperationMethod.TRANSFER_BETWEEN_SUB_ACTIONS, amount,
            request.description(), user, null, date);
        entry(operation, action, from, amount.negate());
        entry(operation, action, to, amount);
        return mapper.operation(operation);
    }

    // Método B: move saldo de uma subação para a subação de outra ação; os totais das duas ações mudam
    @Transactional
    public OperationResponse transferBetweenActions(TransferRequest request) {
        User user = access.requireUpdate();
        requireDifferentSubActions(request);
        UUID fromActionId = actionIdOf(request.fromSubActionId());
        UUID toActionId = actionIdOf(request.toSubActionId());
        if (fromActionId.equals(toActionId)) {
            throw new BusinessException("As subações pertencem à mesma ação; use o Método A (transferência entre subações)");
        }

        Map<UUID, FinanceAction> actions = lockActions(fromActionId, toActionId);
        FinanceAction fromAction = actions.get(fromActionId);
        FinanceAction toAction = actions.get(toActionId);
        requireActive(fromAction);
        requireActive(toAction);
        FinanceSubAction from = subActionOf(request.fromSubActionId(), fromAction);
        FinanceSubAction to = subActionOf(request.toSubActionId(), toAction);
        BigDecimal amount = request.amount().setScale(2);
        LocalDate date = dateOrToday(request.date());
        requireBalance(from, amount);
        // O saldo a distribuir das duas ações não muda (total e subação variam igual); só a origem pode faltar
        checkSubActionOutflow(from, date, amount);

        FinanceOperation operation = newOperation(FinanceOperationMethod.TRANSFER_BETWEEN_ACTIONS, amount,
            request.description(), user, null, date);
        entry(operation, fromAction, from, amount.negate());
        entry(operation, fromAction, null, amount.negate());
        entry(operation, toAction, null, amount);
        entry(operation, toAction, to, amount);
        return mapper.operation(operation);
    }

    // Método C (TAC): soma valor à ação; o que não for alocado nas subações fica como saldo a distribuir
    @Transactional
    public OperationResponse tac(TacRequest request) {
        User user = access.requireUpdate();
        FinanceAction action = lockActions(request.actionId()).get(request.actionId());
        requireActive(action);
        BigDecimal amount = request.amount().setScale(2);
        List<AllocationRequest> allocations = request.allocations() != null ? request.allocations() : List.of();
        Map<FinanceSubAction, BigDecimal> resolved = resolveAllocations(allocations, action);
        BigDecimal allocated = sum(resolved);
        if (allocated.compareTo(amount) > 0) {
            throw new BusinessException("As alocações somam " + money(allocated) + ", mais que o valor do TAC ("
                + money(amount) + ")");
        }

        // TAC só acrescenta valores, então qualquer data passada mantém a linha do tempo válida
        FinanceOperation operation = newOperation(FinanceOperationMethod.TAC, amount, request.description(), user, null,
            dateOrToday(request.date()));
        entry(operation, action, null, amount);
        resolved.forEach((sub, value) -> entry(operation, action, sub, value));
        return mapper.operation(operation);
    }

    // Reparte o saldo a distribuir da ação entre as subações dela
    @Transactional
    public OperationResponse distribute(DistributionRequest request) {
        User user = access.requireUpdate();
        FinanceAction action = lockActions(request.actionId()).get(request.actionId());
        requireActive(action);
        Map<FinanceSubAction, BigDecimal> resolved = resolveAllocations(request.allocations(), action);
        BigDecimal amount = sum(resolved);
        BigDecimal undistributed = action.getTotalAmount().subtract(subActionRepository.sumBalanceByActionId(action.getId()));
        if (amount.compareTo(undistributed) > 0) {
            throw new BusinessException("Saldo a distribuir insuficiente na ação " + action.getCode() + ": disponível "
                + money(undistributed) + ", solicitado " + money(amount));
        }
        LocalDate date = dateOrToday(request.date());
        FinanceTimeline.checkAction(action.getCode(), entryRepository.findByActionIdInOrder(action.getId()), date,
            FinanceMapper.ZERO, amount);

        FinanceOperation operation = newOperation(FinanceOperationMethod.DISTRIBUTION, amount, request.description(),
            user, null, date);
        resolved.forEach((sub, value) -> entry(operation, action, sub, value));
        return mapper.operation(operation);
    }

    // Valor inicial informado ao criar a ação: entra todo como saldo a distribuir
    @Transactional
    public void recordOpening(FinanceAction action, BigDecimal amount, LocalDate date, User user) {
        FinanceOperation operation = newOperation(FinanceOperationMethod.OPENING, amount.setScale(2),
            "Valor inicial da ação " + action.getCode(), user, null, dateOrToday(date));
        entry(operation, action, null, amount.setScale(2));
    }

    // ---------------------------------------------------------------- apoio

    static LocalDate dateOrToday(LocalDate date) {
        return date != null ? date : LocalDate.now();
    }

    // Saída de dotação da subação na data: o disponível precisa continuar >= 0 em todos os dias seguintes
    private void checkSubActionOutflow(FinanceSubAction sub, LocalDate date, BigDecimal amount) {
        FinanceTimeline.checkSubAction(sub.getCode(), entryRepository.findBySubActionIdInOrder(sub.getId()), date,
            amount.negate(), FinanceMapper.ZERO);
    }

    // date nulo = hoje
    FinanceOperation newOperation(FinanceOperationMethod method, BigDecimal amount, String description, User user,
            FinanceCommitment commitment, LocalDate date) {
        LocalDateTime now = LocalDateTime.now();
        return operationRepository.saveAndFlush(FinanceOperation.builder()
            .method(method)
            .amount(amount)
            .description(description != null && !description.isBlank() ? description.trim() : null)
            .commitment(commitment)
            .performedBy(user)
            .operationDate(date != null ? date : now.toLocalDate())
            .occurredAt(now)
            .build());
    }

    private void entry(FinanceOperation operation, FinanceAction action, FinanceSubAction sub, BigDecimal amount) {
        entry(operation, action, sub, FinanceEntryKind.BUDGET, amount);
    }

    // Grava o lançamento e aplica o valor no campo correspondente da ação ou da subação
    void entry(FinanceOperation operation, FinanceAction action, FinanceSubAction sub, FinanceEntryKind kind,
            BigDecimal amount) {
        if (sub == null && kind != FinanceEntryKind.BUDGET) {
            throw new IllegalArgumentException("Lançamento de execução exige subação");
        }
        entryRepository.save(FinanceEntry.builder()
            .operation(operation)
            .action(action)
            .subAction(sub)
            .kind(kind)
            .amount(amount)
            .build());
        if (sub == null) {
            action.setTotalAmount(action.getTotalAmount().add(amount));
            return;
        }
        switch (kind) {
            case BUDGET -> sub.setBalance(sub.getBalance().add(amount));
            case COMMITTED -> sub.setCommittedAmount(sub.getCommittedAmount().add(amount));
            case LIQUIDATED -> sub.setLiquidatedAmount(sub.getLiquidatedAmount().add(amount));
            case PAID -> sub.setPaidAmount(sub.getPaidAmount().add(amount));
        }
    }

    // Trava as ações sempre na mesma ordem (por id) para evitar deadlock entre operações simultâneas
    Map<UUID, FinanceAction> lockActions(UUID... ids) {
        Map<UUID, FinanceAction> locked = new LinkedHashMap<>();
        Stream.of(ids).distinct().sorted().forEach(id -> locked.put(id, actionRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new NotFoundException("Ação não encontrada: " + id))));
        return locked;
    }

    UUID actionIdOf(UUID subActionId) {
        return subActionRepository.findActionIdById(subActionId)
            .orElseThrow(() -> new NotFoundException("Subação não encontrada: " + subActionId));
    }

    // Lida depois da trava da ação, então o saldo está atualizado
    FinanceSubAction subActionOf(UUID subActionId, FinanceAction action) {
        FinanceSubAction sub = subActionRepository.findById(subActionId)
            .orElseThrow(() -> new NotFoundException("Subação não encontrada: " + subActionId));
        if (!sub.getAction().getId().equals(action.getId())) {
            throw new BusinessException("A subação " + sub.getCode() + " não pertence à ação " + action.getCode());
        }
        if (!Boolean.TRUE.equals(sub.getIsActive())) {
            throw new BusinessException("A subação " + sub.getCode() + " está inativa");
        }
        return sub;
    }

    private Map<FinanceSubAction, BigDecimal> resolveAllocations(List<AllocationRequest> allocations, FinanceAction action) {
        Set<UUID> seen = new HashSet<>();
        Map<FinanceSubAction, BigDecimal> resolved = new LinkedHashMap<>();
        for (int i = 0; i < allocations.size(); i++) {
            AllocationRequest allocation = allocations.get(i);
            if (!seen.add(allocation.subActionId())) {
                throw new FieldValidationException("Dados inválidos",
                    Map.of("allocations[" + i + "].subActionId", "subação repetida"));
            }
            resolved.put(subActionOf(allocation.subActionId(), action), allocation.amount().setScale(2));
        }
        return resolved;
    }

    private static BigDecimal sum(Map<FinanceSubAction, BigDecimal> allocations) {
        return allocations.values().stream().reduce(FinanceMapper.ZERO, BigDecimal::add);
    }

    private static void requireDifferentSubActions(TransferRequest request) {
        if (request.fromSubActionId().equals(request.toSubActionId())) {
            throw new BusinessException("A subação de origem e a de destino são a mesma");
        }
    }

    static void requireActive(FinanceAction action) {
        if (!Boolean.TRUE.equals(action.getIsActive())) {
            throw new BusinessException("A ação " + action.getCode() + " está inativa");
        }
    }

    // Só o que não está empenhado pode ser transferido ou empenhado
    static void requireBalance(FinanceSubAction sub, BigDecimal amount) {
        if (sub.availableAmount().compareTo(amount) < 0) {
            throw new BusinessException("Saldo disponível insuficiente na subação " + sub.getCode() + ": disponível "
                + money(sub.availableAmount()) + " (dotação " + money(sub.getBalance()) + ", empenhado "
                + money(sub.getCommittedAmount()) + "), solicitado " + money(amount));
        }
    }
}
