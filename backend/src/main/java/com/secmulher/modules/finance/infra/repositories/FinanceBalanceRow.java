package com.secmulher.modules.finance.infra.repositories;

import java.math.BigDecimal;
import java.util.UUID;

import com.secmulher.modules.finance.infra.entities.enums.FinanceEntryKind;

// Soma dos lançamentos de um tipo até uma data: subActionId nulo = valor total da ação
public record FinanceBalanceRow(UUID actionId, UUID subActionId, FinanceEntryKind kind, BigDecimal amount) {
}
