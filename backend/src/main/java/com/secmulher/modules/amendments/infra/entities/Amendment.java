package com.secmulher.modules.amendments.infra.entities;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.secmulher.modules.amendments.infra.entities.enums.AmendmentStatus;
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

// Emenda parlamentar: uma linha da planilha de emendas (LOA). As colunas de status da planilha são texto
// livre; status é a situação controlada pelo sistema e lastActivityAt marca a última atividade registrada.
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "amendments")
@Entity
public class Amendment {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    // ---------------------------------------------------------------- identificação

    // Ano da LOA
    @Column(name = "exercise_year", nullable = false)
    private Integer exerciseYear;

    @Column(name = "cycle", length = 50)
    private String cycle;

    @Column(name = "parliamentarian", nullable = false)
    private String parliamentarian;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "alepe_code", length = 50)
    private String alepeCode;

    // COD: chave da importação, único no ano
    @Column(name = "code", length = 50)
    private String code;

    @Column(name = "action_code", length = 50)
    private String actionCode;

    @Column(name = "action_name")
    private String actionName;

    @Column(name = "sub_action")
    private String subAction;

    // GD: grupo de despesa
    @Column(name = "expense_group", length = 20)
    private String expenseGroup;

    // MOD: modalidade de aplicação
    @Column(name = "application_modality", length = 20)
    private String applicationModality;

    @Column(name = "beneficiary")
    private String beneficiary;

    @Column(name = "municipality", length = 150)
    private String municipality;

    @Column(name = "object", columnDefinition = "text")
    private String object;

    @Column(name = "type", length = 100)
    private String type;

    @Column(name = "under_change")
    private String underChange;

    @Column(name = "current_amount", precision = 15, scale = 2)
    private BigDecimal currentAmount;

    // ---------------------------------------------------------------- monitoramento (texto livre)

    @Column(name = "financial_status")
    private String financialStatus;

    @Column(name = "seplag_impediment", columnDefinition = "text")
    private String seplagImpediment;

    @Column(name = "documental_status")
    private String documentalStatus;

    @Column(name = "details", columnDefinition = "text")
    private String details;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    // ---------------------------------------------------------------- responsáveis

    // ÁREA TÉCNICA como veio da planilha
    @Column(name = "technical_area")
    private String technicalArea;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sector_id")
    private AmendmentSector sector;

    // Técnica responsável: usuário do sistema (opcional) e nome
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsible_user_id")
    private User responsibleUser;

    @Column(name = "responsible_name")
    private String responsibleName;

    // ---------------------------------------------------------------- processo e Termo de Fomento

    @Column(name = "sei_number", length = 50)
    private String seiNumber;

    @Column(name = "technical_opinion", columnDefinition = "text")
    private String technicalOpinion;

    @Column(name = "term_number", length = 50)
    private String termNumber;

    // Vigência do Termo de Fomento: prazo limite para concluir a emenda
    @Column(name = "term_end_date")
    private LocalDate termEndDate;

    // ---------------------------------------------------------------- execução financeira

    @Column(name = "pf_number", length = 50)
    private String pfNumber;

    @Column(name = "pf_date")
    private LocalDate pfDate;

    @Column(name = "sefaz_pf", length = 100)
    private String sefazPf;

    @Column(name = "commitment_number", length = 50)
    private String commitmentNumber;

    @Column(name = "dh_number", length = 50)
    private String dhNumber;

    @Column(name = "le_number", length = 50)
    private String leNumber;

    @Column(name = "pd", length = 100)
    private String pd;

    @Column(name = "ob_number", length = 50)
    private String obNumber;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    // ---------------------------------------------------------------- sistema GTV

    @Column(name = "call_number", length = 50)
    private String callNumber;

    @Column(name = "proposal_number", length = 50)
    private String proposalNumber;

    // SITUAÇÃO como veio da planilha
    @Column(name = "situation")
    private String situation;

    // ---------------------------------------------------------------- controle do sistema

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AmendmentStatus status;

    @Column(name = "last_activity_at", nullable = false)
    private LocalDateTime lastActivityAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by")
    private User completedBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private User cancelledBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
