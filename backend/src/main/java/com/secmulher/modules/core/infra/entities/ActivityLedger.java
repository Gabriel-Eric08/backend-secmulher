package com.secmulher.modules.core.infra.entities;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.secmulher.modules.core.infra.entities.enums.ActivityType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Registro imutável: todas as colunas são updatable = false
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "activities_ledger", indexes = {
    @Index(name = "idx_ledger_polymorphic", columnList = "entity_type, entity_id"),
    @Index(name = "idx_ledger_user", columnList = "user_id")
})
@Entity
public class ActivityLedger {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    // Polimorfismo: tabela de origem (ex: 'clients' ou 'dynamic_entities')
    @Column(name = "entity_type", nullable = false, length = 100, updatable = false)
    private String entityType;

    // Polimorfismo: registro específico dentro da tabela
    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 50, updatable = false)
    private ActivityType actionType;

    // Tipo configurável do registro manual (action_type = MANUAL); nulo em eventos automáticos
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_type_id", updatable = false)
    private CategoryActivityType activityType;

    // Nome do tipo no momento do registro: renomear o tipo depois não altera o histórico
    @Column(name = "activity_type_label", updatable = false)
    private String activityTypeLabel;

    @Column(name = "description", columnDefinition = "text", updatable = false)
    private String description;

    // Ex: {"telefone": {"antigo": "1111", "novo": "2222"}}
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changes", columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> changes;

    // Funcionário que realizou o atendimento (pode não ser usuário do sistema)
    @Column(name = "responsible_employee", updatable = false)
    private String responsibleEmployee;

    // Quando a atividade aconteceu (pode ser anterior ao registro no sistema)
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
