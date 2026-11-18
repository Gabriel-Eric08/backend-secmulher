package com.secmulher.modules.core.infra.entities;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

// Tipo de registro manual da linha do tempo, configurado pelo administrador em cada categoria
// (ex: 'Atendimento psicológico' em "Mulheres atendidas", 'Matrícula' em "Cursos")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "activity_types", uniqueConstraints = @UniqueConstraint(columnNames = {"category_id", "key"}))
@Entity
public class CategoryActivityType {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    // Identificador estável: não muda depois de criado
    @Column(name = "key", nullable = false, length = 100, updatable = false)
    private String key;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "color", length = 20)
    private String color;

    @Column(name = "position")
    private Integer position;

    @Column(name = "is_active")
    private Boolean isActive;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
