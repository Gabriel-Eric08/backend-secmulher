package com.secmulher.modules.finance.domain.services;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.secmulher.modules.finance.domain.dtos.FinanceActionDtos.FinanceActionResponse;
import com.secmulher.modules.finance.domain.dtos.FinanceCommitmentDtos.CommitmentResponse;
import com.secmulher.modules.finance.domain.dtos.FinanceCommitmentDtos.CommitmentStatus;
import com.secmulher.modules.finance.domain.dtos.FinanceOperationDtos.EntryResponse;
import com.secmulher.modules.finance.domain.dtos.FinanceOperationDtos.OperationResponse;
import com.secmulher.modules.finance.domain.dtos.FinanceSubActionDtos.FinanceSubActionResponse;
import com.secmulher.modules.finance.infra.entities.FinanceAction;
import com.secmulher.modules.finance.infra.entities.FinanceCommitment;
import com.secmulher.modules.finance.infra.entities.FinanceEntry;
import com.secmulher.modules.finance.infra.entities.FinanceOperation;
import com.secmulher.modules.finance.infra.entities.FinanceSubAction;
import com.secmulher.modules.finance.infra.repositories.FinanceEntryRepository;

// Monta as respostas do módulo. Os valores vêm de fora porque podem ser os atuais
// (gravados na ação/subação) ou os de uma data passada (somados a partir dos lançamentos).
@Component
public class FinanceMapper {
    static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    // Dotação e execução de uma subação em um momento
    public record SubValues(BigDecimal balance, BigDecimal committed, BigDecimal liquidated, BigDecimal paid) {
        static final SubValues EMPTY = new SubValues(ZERO, ZERO, ZERO, ZERO);

        static SubValues current(FinanceSubAction sub) {
            return new SubValues(sub.getBalance(), sub.getCommittedAmount(), sub.getLiquidatedAmount(), sub.getPaidAmount());
        }

        BigDecimal available() {
            return balance.subtract(committed);
        }
    }

    // Saídas primeiro, depois entradas; dentro de cada grupo, por código da ação, da subação e tipo
    private static final Comparator<EntryResponse> ENTRY_ORDER = Comparator
        .comparing((EntryResponse entry) -> entry.amount().signum())
        .thenComparing(EntryResponse::actionCode)
        .thenComparing(EntryResponse::subActionCode, Comparator.nullsFirst(Comparator.naturalOrder()))
        .thenComparing(EntryResponse::kind);

    final FinanceEntryRepository entryRepository;

    public FinanceMapper(FinanceEntryRepository entryRepository) {
        this.entryRepository = entryRepository;
    }

    static String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(value);
    }

    static BigDecimal scaled(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2);
    }

    public FinanceSubActionResponse subAction(FinanceSubAction sub, FinanceAction action, SubValues values) {
        BigDecimal balance = scaled(values.balance());
        BigDecimal committed = scaled(values.committed());
        return new FinanceSubActionResponse(
            sub.getId(),
            action.getId(),
            action.getCode(),
            sub.getCode(),
            sub.getName(),
            sub.getDescription(),
            sub.getIsActive(),
            balance,
            committed,
            scaled(values.liquidated()),
            scaled(values.paid()),
            balance.subtract(committed),
            sub.getCreatedBy() != null ? sub.getCreatedBy().getId() : null,
            sub.getCreatedAt(),
            sub.getUpdatedAt()
        );
    }

    public FinanceSubActionResponse currentSubAction(FinanceSubAction sub) {
        return subAction(sub, sub.getAction(), SubValues.current(sub));
    }

    public FinanceActionResponse action(FinanceAction action, List<FinanceSubAction> subs, BigDecimal total,
            Function<FinanceSubAction, SubValues> valuesOf) {
        List<FinanceSubActionResponse> subResponses = subs.stream()
            .map(sub -> subAction(sub, action, valuesOf.apply(sub)))
            .toList();
        BigDecimal distributed = sum(subResponses, FinanceSubActionResponse::balance);
        BigDecimal committed = sum(subResponses, FinanceSubActionResponse::committedAmount);
        BigDecimal totalAmount = scaled(total);
        return new FinanceActionResponse(
            action.getId(),
            action.getCode(),
            action.getName(),
            action.getDescription(),
            action.getIsActive(),
            totalAmount,
            distributed,
            totalAmount.subtract(distributed),
            committed,
            sum(subResponses, FinanceSubActionResponse::liquidatedAmount),
            sum(subResponses, FinanceSubActionResponse::paidAmount),
            totalAmount.subtract(committed),
            action.getCreatedBy() != null ? action.getCreatedBy().getId() : null,
            action.getCreatedAt(),
            action.getUpdatedAt(),
            subResponses
        );
    }

    // Situação atual: valores gravados na ação e nas subações
    public FinanceActionResponse currentAction(FinanceAction action, List<FinanceSubAction> subs) {
        return action(action, subs, action.getTotalAmount(), SubValues::current);
    }

    static <T> BigDecimal sum(Collection<T> items, Function<T, BigDecimal> value) {
        return items.stream().map(value).reduce(ZERO, BigDecimal::add);
    }

    public CommitmentResponse commitment(FinanceCommitment commitment) {
        BigDecimal committed = commitment.committedAmount();
        CommitmentStatus status;
        if (committed.signum() == 0 && commitment.getLiquidatedAmount().signum() == 0) {
            status = CommitmentStatus.CANCELLED;
        } else if (commitment.toLiquidateAmount().signum() > 0) {
            status = CommitmentStatus.TO_LIQUIDATE;
        } else if (commitment.toPayAmount().signum() > 0) {
            status = CommitmentStatus.TO_PAY;
        } else {
            status = CommitmentStatus.PAID;
        }
        return new CommitmentResponse(
            commitment.getId(),
            commitment.getNumber(),
            commitment.getCommitmentDate(),
            commitment.getAction().getId(),
            commitment.getAction().getCode(),
            commitment.getSubAction().getId(),
            commitment.getSubAction().getCode(),
            commitment.getCreditor(),
            commitment.getDescription(),
            scaled(commitment.getAmount()),
            scaled(commitment.getCancelledAmount()),
            scaled(committed),
            scaled(commitment.getLiquidatedAmount()),
            scaled(commitment.getPaidAmount()),
            scaled(commitment.toLiquidateAmount()),
            scaled(commitment.toPayAmount()),
            status,
            commitment.getCreatedBy() != null ? commitment.getCreatedBy().getId() : null,
            commitment.getCreatedAt(),
            commitment.getUpdatedAt()
        );
    }

    public OperationResponse operation(FinanceOperation operation) {
        return operations(List.of(operation)).getFirst();
    }

    // Mantém a ordem recebida; os lançamentos de todas as operações vêm em uma única consulta
    public List<OperationResponse> operations(Collection<FinanceOperation> operations) {
        if (operations.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = operations.stream().map(FinanceOperation::getId).toList();
        Map<UUID, List<EntryResponse>> entriesByOperation = entryRepository.findWithDetailsByOperationIds(ids).stream()
            .collect(Collectors.groupingBy(entry -> entry.getOperation().getId(),
                Collectors.mapping(FinanceMapper::entry, Collectors.toList())));

        return operations.stream()
            .map(operation -> new OperationResponse(
                operation.getId(),
                operation.getNumber(),
                operation.getMethod(),
                operation.getMethod().getLabel(),
                scaled(operation.getAmount()),
                operation.getDescription(),
                operation.getCommitment() != null ? operation.getCommitment().getId() : null,
                operation.getCommitment() != null ? operation.getCommitment().getNumber() : null,
                operation.getPerformedBy() != null ? operation.getPerformedBy().getId() : null,
                operation.getPerformedBy() != null ? operation.getPerformedBy().getName() : null,
                operation.getOperationDate(),
                operation.getOccurredAt(),
                entriesByOperation.getOrDefault(operation.getId(), List.of()).stream().sorted(ENTRY_ORDER).toList()
            ))
            .toList();
    }

    private static EntryResponse entry(FinanceEntry entry) {
        FinanceSubAction sub = entry.getSubAction();
        return new EntryResponse(
            entry.getAction().getId(),
            entry.getAction().getCode(),
            entry.getAction().getName(),
            sub != null ? sub.getId() : null,
            sub != null ? sub.getCode() : null,
            sub != null ? sub.getName() : null,
            entry.getKind(),
            scaled(entry.getAmount())
        );
    }
}
