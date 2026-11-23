package com.secmulher.modules.amendments.infra.entities;

import java.time.LocalDateTime;
import java.util.UUID;

import com.secmulher.modules.amendments.infra.entities.enums.PendencyKind;
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

// Impedimento ou espera de uma emenda. Fica aberta enquanto resolvedAt for nulo
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "amendment_pendencies")
@Entity
public class AmendmentPendency {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "amendment_id", nullable = false, updatable = false)
    private Amendment amendment;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private PendencyKind kind;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    // De quem ou de quê a emenda depende (ex: SEPLAG, beneficiário)
    @Column(name = "waiting_on")
    private String waitingOn;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opened_by")
    private User openedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    @Column(name = "resolution", columnDefinition = "text")
    private String resolution;

    public boolean isOpen() {
        return resolvedAt == null;
    }
}
