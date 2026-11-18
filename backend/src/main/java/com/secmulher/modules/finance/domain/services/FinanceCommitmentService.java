package com.secmulher.modules.finance.domain.services;

import static com.secmulher.modules.finance.domain.services.FinanceMapper.money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.modules.core.domain.exceptions.BusinessException;
import com.secmulher.modules.core.domain.exceptions.ConflictException;
import com.secmulher.modules.core.domain.exceptions.NotFoundException;
import com.secmulher.modules.core.infra.entities.User;
import com.secmulher.modules.finance.domain.access.FinanceAccessService;
import com.secmulher.modules.finance.domain.dtos.FinanceCommitmentDtos.CommitmentCreateRequest;
import com.secmulher.modules.finance.domain.dtos.FinanceCommitmentDtos.CommitmentDetailResponse;
import com.secmulher.modules.finance.domain.dtos.FinanceCommitmentDtos.CommitmentMovementRequest;
import com.secmulher.modules.finance.domain.dtos.FinanceCommitmentDtos.CommitmentResponse;
import com.secmulher.modules.finance.domain.dtos.FinanceCommitmentDtos.CommitmentStatus;
import com.secmulher.modules.finance.domain.dtos.FinanceCommitmentDtos.CommitmentUpdateRequest;
import com.secmulher.modules.finance.infra.entities.FinanceAction;
import com.secmulher.modules.finance.infra.entities.FinanceCommitment;
import com.secmulher.modules.finance.infra.entities.FinanceOperation;
import com.secmulher.modules.finance.infra.entities.FinanceSubAction;
import com.secmulher.modules.finance.infra.entities.enums.FinanceEntryKind;
import com.secmulher.modules.finance.infra.entities.enums.FinanceOperationMethod;
import com.secmulher.modules.finance.infra.repositories.FinanceCommitmentRepository;
import com.secmulher.modules.finance.infra.repositories.FinanceEntryRepository;
import com.secmulher.modules.finance.infra.repositories.FinanceOperationRepository;

// Execução da despesa: empenho -> liquidação -> pagamento, mais a anulação do que ainda não foi liquidado.
// O empenho consome o saldo disponível da subação (dotação - empenhado); a anulação devolve.
// Cada passo é uma operação no histórico, com lançamentos COMMITTED/LIQUIDATED/PAID na subação,
// e roda com a ação travada, como as demais operações.
@Service
public class FinanceCommitmentService {
    final FinanceCommitmentRepository commitmentRepository;
    final FinanceOperationRepository operationRepository;
    final FinanceEntryRepository entryRepository;
    final FinanceOperationService operationService;
    final FinanceAccessService access;
    final FinanceMapper mapper;

    public FinanceCommitmentService(FinanceCommitmentRepository commitmentRepository,
            FinanceOperationRepository operationRepository, FinanceEntryRepository entryRepository,
            FinanceOperationService operationService, FinanceAccessService access, FinanceMapper mapper) {
        this.commitmentRepository = commitmentRepository;
        this.operationRepository = operationRepository;
        this.entryRepository = entryRepository;
        this.operationService = operationService;
        this.access = access;
        this.mapper = mapper;
    }

    private FinanceCommitment getCommitmentById(UUID id) {
        return commitmentRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Empenho não encontrado: " + id));
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // ---------------------------------------------------------------- consultas

    @Transactional(readOnly = true)
    public List<CommitmentResponse> findAll(UUID actionId, UUID subActionId, String creditor, CommitmentStatus status) {
        access.requireRead();
        return commitmentRepository.search(actionId, subActionId, normalize(creditor)).stream()
            .map(mapper::commitment)
            .filter(commitment -> status == null || commitment.status() == status)
            .toList();
    }

    @Transactional(readOnly = true)
    public CommitmentDetailResponse findById(UUID id) {
        access.requireRead();
        FinanceCommitment commitment = getCommitmentById(id);
        return detail(commitment);
    }

    // ---------------------------------------------------------------- cadastro

    @Transactional
    public CommitmentDetailResponse create(CommitmentCreateRequest request) {
        User user = access.requireUpdate();
        String number = request.number().trim();
        if (commitmentRepository.existsByNumberIgnoreCase(number)) {
            throw new ConflictException("Já existe um empenho com o número: " + number);
        }

        UUID actionId = operationService.actionIdOf(request.subActionId());
        FinanceAction action = operationService.lockActions(actionId).get(actionId);
        FinanceOperationService.requireActive(action);
        FinanceSubAction sub = operationService.subActionOf(request.subActionId(), action);
        BigDecimal amount = request.amount().setScale(2);
        LocalDate date = request.date() != null ? request.date() : LocalDate.now();
        FinanceOperationService.requireBalance(sub, amount);
        // Empenho retroativo: a dotação precisa cobrir o empenhado em todos os dias desde a data informada
        FinanceTimeline.checkSubAction(sub.getCode(), entryRepository.findBySubActionIdInOrder(sub.getId()),
            date, FinanceMapper.ZERO, amount);

        FinanceCommitment commitment = commitmentRepository.saveAndFlush(FinanceCommitment.builder()
            .number(number)
            .commitmentDate(date)
            .subAction(sub)
            .action(action)
            .creditor(request.creditor().trim())
            .description(normalize(request.description()))
            .amount(amount)
            .cancelledAmount(FinanceMapper.ZERO)
            .liquidatedAmount(FinanceMapper.ZERO)
            .paidAmount(FinanceMapper.ZERO)
            .createdBy(user)
            .build());

        FinanceOperation operation = operationService.newOperation(FinanceOperationMethod.COMMITMENT, amount,
            describe("Empenho " + number + " - " + commitment.getCreditor(), request.description()), user, commitment, date);
        operationService.entry(operation, action, sub, FinanceEntryKind.COMMITTED, amount);
        return detail(commitment);
    }

    // Número, favorecido e descrição; o valor só muda por anulação
    @Transactional
    public CommitmentResponse update(UUID id, CommitmentUpdateRequest request) {
        access.requireUpdate();
        FinanceCommitment commitment = getCommitmentById(id);
        String number = request.number().trim();
        if (commitmentRepository.existsByNumberIgnoreCaseAndIdNot(number, id)) {
            throw new ConflictException("Já existe um empenho com o número: " + number);
        }
        commitment.setNumber(number);
        commitment.setCreditor(request.creditor().trim());
        commitment.setDescription(normalize(request.description()));
        return mapper.commitment(commitmentRepository.saveAndFlush(commitment));
    }

    // ---------------------------------------------------------------- execução
    // Liquidação, pagamento e anulação valem mesmo com a ação ou a subação inativa:
    // a inativação só impede novos empenhos e transferências.

    @Transactional
    public CommitmentDetailResponse liquidate(UUID id, CommitmentMovementRequest request) {
        return move(id, request, FinanceOperationMethod.LIQUIDATION);
    }

    @Transactional
    public CommitmentDetailResponse pay(UUID id, CommitmentMovementRequest request) {
        return move(id, request, FinanceOperationMethod.PAYMENT);
    }

    // Anula a parte ainda não liquidada; o valor volta a ficar disponível na subação
    @Transactional
    public CommitmentDetailResponse cancel(UUID id, CommitmentMovementRequest request) {
        return move(id, request, FinanceOperationMethod.COMMITMENT_CANCELLATION);
    }

    // Liquidação, pagamento ou anulação com data real (padrão: hoje), nunca anterior à data do empenho.
    // Confere os valores atuais e, para datas no passado, a linha do tempo do empenho dia a dia
    private CommitmentDetailResponse move(UUID id, CommitmentMovementRequest request, FinanceOperationMethod method) {
        User user = access.requireUpdate();
        Locked locked = lock(id);
        FinanceCommitment commitment = locked.commitment();
        BigDecimal amount = request.amount().setScale(2);
        LocalDate date = request.date() != null ? request.date() : LocalDate.now();
        String number = commitment.getNumber();
        if (date.isBefore(commitment.getCommitmentDate())) {
            throw new BusinessException("A data (" + FinanceTimeline.day(date) + ") não pode ser anterior à data do empenho "
                + number + " (" + FinanceTimeline.day(commitment.getCommitmentDate()) + ")");
        }

        switch (method) {
            case LIQUIDATION -> {
                if (amount.compareTo(commitment.toLiquidateAmount()) > 0) {
                    throw new BusinessException("Valor a liquidar do empenho " + number + " é "
                        + money(commitment.toLiquidateAmount()) + "; solicitado " + money(amount));
                }
            }
            case PAYMENT -> {
                if (amount.compareTo(commitment.toPayAmount()) > 0) {
                    throw new BusinessException("Valor a pagar do empenho " + number + " é "
                        + money(commitment.toPayAmount()) + " (só se paga o que já foi liquidado); solicitado " + money(amount));
                }
            }
            case COMMITMENT_CANCELLATION -> {
                if (amount.compareTo(commitment.toLiquidateAmount()) > 0) {
                    throw new BusinessException("Só a parte não liquidada pode ser anulada: o empenho " + number + " tem "
                        + money(commitment.toLiquidateAmount()) + " a liquidar; solicitado " + money(amount));
                }
            }
            default -> throw new IllegalArgumentException("Operação de empenho inválida: " + method);
        }
        FinanceTimeline.checkCommitment(number, operationsOf(commitment), method, date, amount);

        FinanceEntryKind kind;
        BigDecimal entryAmount = amount;
        String fallback;
        switch (method) {
            case LIQUIDATION -> {
                commitment.setLiquidatedAmount(commitment.getLiquidatedAmount().add(amount));
                kind = FinanceEntryKind.LIQUIDATED;
                fallback = "Liquidação do empenho " + number;
            }
            case PAYMENT -> {
                commitment.setPaidAmount(commitment.getPaidAmount().add(amount));
                kind = FinanceEntryKind.PAID;
                fallback = "Pagamento do empenho " + number;
            }
            default -> {
                commitment.setCancelledAmount(commitment.getCancelledAmount().add(amount));
                kind = FinanceEntryKind.COMMITTED;
                entryAmount = amount.negate();
                fallback = "Anulação do empenho " + number;
            }
        }
        FinanceOperation operation = operationService.newOperation(method, amount,
            describe(fallback, request.description()), user, commitment, date);
        operationService.entry(operation, locked.action(), commitment.getSubAction(), kind, entryAmount);
        return detail(commitment);
    }

    // ---------------------------------------------------------------- apoio

    private record Locked(FinanceAction action, FinanceCommitment commitment) {}

    // Trava a ação do empenho e só então carrega o empenho, para os valores estarem atualizados
    private Locked lock(UUID commitmentId) {
        UUID actionId = commitmentRepository.findActionIdById(commitmentId)
            .orElseThrow(() -> new NotFoundException("Empenho não encontrado: " + commitmentId));
        FinanceAction action = operationService.lockActions(actionId).get(actionId);
        return new Locked(action, getCommitmentById(commitmentId));
    }

    private static String describe(String fallback, String description) {
        return description != null && !description.isBlank() ? description.trim() : fallback;
    }

    private List<FinanceOperation> operationsOf(FinanceCommitment commitment) {
        return operationRepository.findAll(
            (root, query, cb) -> cb.equal(root.get("commitment").get("id"), commitment.getId()),
            FinanceOperationService.NEWEST_FIRST);
    }

    private CommitmentDetailResponse detail(FinanceCommitment commitment) {
        List<FinanceOperation> operations = operationsOf(commitment);
        return new CommitmentDetailResponse(mapper.commitment(commitment), mapper.operations(operations));
    }
}
