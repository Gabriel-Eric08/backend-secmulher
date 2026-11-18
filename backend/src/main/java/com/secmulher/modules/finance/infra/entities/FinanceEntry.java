package com.secmulher.modules.finance.infra.entities;

import java.math.BigDecimal;
import java.util.UUID;

import com.secmulher.modules.finance.infra.entities.enums.FinanceEntryKind;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Lançamento (imutável) de uma operação, com sinal: positivo entra, negativo sai.
// Sem subação, altera o valor total da ação; com subação, altera o saldo da subação.
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "finance_entries")
@Entity
public class FinanceEntry {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operation_id", nullable = false, updatable = false)
    private FinanceOperation operation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "action_id", nullable = false, updatable = false)
    private FinanceAction action;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_action_id", updatable = false)
    private FinanceSubAction subAction;

    // BUDGET mexe na dotação; COMMITTED/LIQUIDATED/PAID (sempre com subação) na execução da despesa
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20, updatable = false)
    private FinanceEntryKind kind;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;
}
