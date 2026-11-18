package com.secmulher.modules.finance.infra.entities.enums;

// O que um lançamento altera. BUDGET é a dotação (valor total da ação ou saldo da subação);
// os demais acumulam a execução da despesa na subação. Gravado em finance_entries.kind
public enum FinanceEntryKind {
    BUDGET,
    COMMITTED,
    LIQUIDATED,
    PAID
}
