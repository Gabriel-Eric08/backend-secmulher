package com.secmulher.modules.finance.infra.entities;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.Generated;

import com.secmulher.modules.core.infra.entities.User;
import com.secmulher.modules.finance.infra.entities.enums.FinanceOperationMethod;

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

// Operação financeira (imutável). Os valores movimentados ficam em FinanceEntry
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "finance_operations")
@Entity
public class FinanceOperation {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    // Número sequencial gerado pelo banco: identifica a operação e define a ordem do histórico
    @Generated
    @Column(name = "number", insertable = false, updatable = false)
    private Long number;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 40, updatable = false)
    private FinanceOperationMethod method;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "description", columnDefinition = "text", updatable = false)
    private String description;

    // Empenho a que a operação se refere (empenho, anulação, liquidação e pagamento)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commitment_id", updatable = false)
    private FinanceCommitment commitment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by", updatable = false)
    private User performedBy;

    // Data real da operação (ex.: data do empenho ou do pagamento); define os saldos em cada dia
    // e a ordem do histórico. Transferências, TAC e distribuição usam a data do registro
    @Column(name = "operation_date", nullable = false, updatable = false)
    private LocalDate operationDate;

    // Momento em que a operação foi registrada no sistema
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;
}
