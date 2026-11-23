package com.secmulher.modules.amendments.infra.entities;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.secmulher.modules.amendments.infra.entities.enums.AmendmentActivityKind;
import com.secmulher.modules.core.infra.entities.User;

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

// Registro imutável da linha do tempo da emenda: todas as colunas são updatable = false
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "amendment_activities")
@Entity
public class AmendmentActivity {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "amendment_id", nullable = false, updatable = false)
    private Amendment amendment;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 30, updatable = false)
    private AmendmentActivityKind kind;

    @Column(name = "description", columnDefinition = "text", updatable = false)
    private String description;

    // Ex: {"financialStatus": {"antigo": "Aguardando PF", "novo": "PF autorizada"}}
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changes", columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> changes;

    // Pendência aberta ou resolvida por este registro (PENDENCY_OPENED / PENDENCY_RESOLVED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pendency_id", updatable = false)
    private AmendmentPendency pendency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", updatable = false)
    private User user;

    // Quando a atividade aconteceu (pode ser anterior ao registro no sistema)
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
