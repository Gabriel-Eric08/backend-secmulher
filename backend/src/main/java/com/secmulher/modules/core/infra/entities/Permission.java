package com.secmulher.modules.core.infra.entities;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.secmulher.modules.core.infra.entities.enums.ConfidentialityLevel;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "permissions", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "category_id"}))
@Entity
public class Permission {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "can_read")
    private Boolean canRead;

    @Column(name = "can_create")
    private Boolean canCreate;

    @Column(name = "can_update")
    private Boolean canUpdate;

    @Column(name = "can_delete")
    private Boolean canDelete;

    // Ex: {"read": ["nome", "telefone", "sob_risco"], "write": ["telefone"]}
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_columns", columnDefinition = "jsonb")
    private Map<String, Object> allowedColumns;

    // Keys dos tipos de registro manual que o usuário pode registrar; nulo = todos
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_activity_types", columnDefinition = "jsonb")
    private List<String> allowedActivityTypes;

    // Nível máximo de sigilo de documento que o usuário acessa nesta categoria
    @Enumerated(EnumType.STRING)
    @Column(name = "max_confidentiality", nullable = false, length = 20)
    private ConfidentialityLevel maxConfidentiality;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
