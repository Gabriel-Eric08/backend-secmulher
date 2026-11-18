package com.secmulher.modules.finance.infra.entities;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.secmulher.modules.core.infra.entities.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

// Empenho (nota de empenho) de uma subação. Os valores acumulados respeitam
// pago <= liquidado <= empenhado (amount - cancelledAmount)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "finance_commitments")
@Entity
public class FinanceCommitment {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    @Column(name = "number", nullable = false, length = 50)
    private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sub_action_id", nullable = false, updatable = false)
    private FinanceSubAction subAction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "action_id", nullable = false, updatable = false)
    private FinanceAction action;

    // Data real do empenho (a mesma da operação de empenho)
    @Column(name = "commitment_date", nullable = false, updatable = false)
    private LocalDate commitmentDate;

    // Favorecido (credor)
    @Column(name = "creditor", nullable = false)
    private String creditor;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    // Valor original do empenho
    @Column(name = "amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "cancelled_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal cancelledAmount;

    @Column(name = "liquidated_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal liquidatedAmount;

    @Column(name = "paid_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal paidAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Empenhado líquido: valor original menos o anulado
    public BigDecimal committedAmount() {
        return amount.subtract(cancelledAmount);
    }

    public BigDecimal toLiquidateAmount() {
        return committedAmount().subtract(liquidatedAmount);
    }

    public BigDecimal toPayAmount() {
        return liquidatedAmount.subtract(paidAmount);
    }
}
