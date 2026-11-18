package com.secmulher.modules.finance.domain.services;

import static com.secmulher.modules.finance.domain.services.FinanceMapper.ZERO;
import static com.secmulher.modules.finance.domain.services.FinanceMapper.money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.secmulher.modules.core.domain.exceptions.BusinessException;
import com.secmulher.modules.finance.infra.entities.FinanceEntry;
import com.secmulher.modules.finance.infra.entities.FinanceOperation;
import com.secmulher.modules.finance.infra.entities.enums.FinanceEntryKind;
import com.secmulher.modules.finance.infra.entities.enums.FinanceOperationMethod;

// Validação das operações pela data real. Uma operação com data no passado muda os valores de todos os
// dias seguintes; então a linha do tempo é refeita com a operação nova e as regras são conferidas no fim
// de cada dia em que algo aconteceu:
//   subação: empenhado <= dotação (disponível nunca negativo)
//   ação:    soma das dotações das subações <= valor total (saldo a distribuir nunca negativo)
//   empenho: pago <= liquidado <= empenhado (valor - anulado)
final class FinanceTimeline {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private FinanceTimeline() {}

    static String day(LocalDate date) {
        return DAY.format(date);
    }

    // Variações de um dia
    private static final class Day {
        BigDecimal a = ZERO;
        BigDecimal b = ZERO;
        BigDecimal c = ZERO;
    }

    // Subação: aplica as variações de dotação e de empenhado na data e confere o disponível dia a dia
    static void checkSubAction(String subActionCode, List<FinanceEntry> subActionEntries, LocalDate date,
            BigDecimal budgetDelta, BigDecimal committedDelta) {
        TreeMap<LocalDate, Day> days = new TreeMap<>();
        for (FinanceEntry entry : subActionEntries) {
            Day day = days.computeIfAbsent(entry.getOperation().getOperationDate(), key -> new Day());
            if (entry.getKind() == FinanceEntryKind.BUDGET) {
                day.a = day.a.add(entry.getAmount());
            } else if (entry.getKind() == FinanceEntryKind.COMMITTED) {
                day.b = day.b.add(entry.getAmount());
            }
        }
        Day target = days.computeIfAbsent(date, key -> new Day());
        target.a = target.a.add(budgetDelta);
        target.b = target.b.add(committedDelta);

        BigDecimal budget = ZERO;
        BigDecimal committed = ZERO;
        for (Map.Entry<LocalDate, Day> day : days.entrySet()) {
            budget = budget.add(day.getValue().a);
            committed = committed.add(day.getValue().b);
            if (committed.compareTo(budget) > 0) {
                throw new BusinessException("Com a data " + day(date) + ", a subação " + subActionCode + " ficaria em "
                    + day(day.getKey()) + " com dotação de " + money(budget) + " e empenhado de " + money(committed)
                    + " (disponível negativo)");
            }
        }
    }

    // Ação: aplica as variações do valor total e do distribuído na data e confere o saldo a distribuir dia a dia
    static void checkAction(String actionCode, List<FinanceEntry> actionEntries, LocalDate date,
            BigDecimal totalDelta, BigDecimal distributedDelta) {
        TreeMap<LocalDate, Day> days = new TreeMap<>();
        for (FinanceEntry entry : actionEntries) {
            if (entry.getKind() != FinanceEntryKind.BUDGET) {
                continue;
            }
            Day day = days.computeIfAbsent(entry.getOperation().getOperationDate(), key -> new Day());
            if (entry.getSubAction() == null) {
                day.a = day.a.add(entry.getAmount());
            } else {
                day.b = day.b.add(entry.getAmount());
            }
        }
        Day target = days.computeIfAbsent(date, key -> new Day());
        target.a = target.a.add(totalDelta);
        target.b = target.b.add(distributedDelta);

        BigDecimal total = ZERO;
        BigDecimal distributed = ZERO;
        for (Map.Entry<LocalDate, Day> day : days.entrySet()) {
            total = total.add(day.getValue().a);
            distributed = distributed.add(day.getValue().b);
            if (distributed.compareTo(total) > 0) {
                throw new BusinessException("Com a data " + day(date) + ", a ação " + actionCode + " ficaria em "
                    + day(day.getKey()) + " com valor total de " + money(total) + " e " + money(distributed)
                    + " distribuídos nas subações (saldo a distribuir negativo)");
            }
        }
    }

    // Empenho: aplica a nova operação (liquidação, pagamento ou anulação) e confere a ordem empenho > liquidação > pagamento
    static void checkCommitment(String commitmentNumber, List<FinanceOperation> commitmentOperations,
            FinanceOperationMethod method, LocalDate date, BigDecimal amount) {
        TreeMap<LocalDate, Day> days = new TreeMap<>();
        for (FinanceOperation operation : commitmentOperations) {
            apply(days.computeIfAbsent(operation.getOperationDate(), key -> new Day()), operation.getMethod(),
                operation.getAmount());
        }
        apply(days.computeIfAbsent(date, key -> new Day()), method, amount);

        BigDecimal committed = ZERO;
        BigDecimal liquidated = ZERO;
        BigDecimal paid = ZERO;
        for (Map.Entry<LocalDate, Day> day : days.entrySet()) {
            committed = committed.add(day.getValue().a);
            liquidated = liquidated.add(day.getValue().b);
            paid = paid.add(day.getValue().c);
            if (liquidated.compareTo(committed) > 0) {
                throw new BusinessException("Com a data " + day(date) + ", o empenho " + commitmentNumber + " ficaria em "
                    + day(day.getKey()) + " com liquidado de " + money(liquidated) + ", acima do empenhado daquele dia ("
                    + money(committed) + ")");
            }
            if (paid.compareTo(liquidated) > 0) {
                throw new BusinessException("Com a data " + day(date) + ", o empenho " + commitmentNumber + " ficaria em "
                    + day(day.getKey()) + " com pago de " + money(paid) + ", acima do liquidado daquele dia ("
                    + money(liquidated) + ")");
            }
        }
    }

    private static void apply(Day day, FinanceOperationMethod method, BigDecimal amount) {
        switch (method) {
            case COMMITMENT -> day.a = day.a.add(amount);
            case COMMITMENT_CANCELLATION -> day.a = day.a.subtract(amount);
            case LIQUIDATION -> day.b = day.b.add(amount);
            case PAYMENT -> day.c = day.c.add(amount);
            default -> { }
        }
    }
}
