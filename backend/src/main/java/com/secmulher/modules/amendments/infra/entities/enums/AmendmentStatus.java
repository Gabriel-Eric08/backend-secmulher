package com.secmulher.modules.amendments.infra.entities.enums;

// Situação da emenda controlada pelo sistema (base dos filtros). IN_PROGRESS, WAITING e BLOCKED seguem as
// pendências abertas: impedimento aberto -> BLOCKED; senão espera aberta -> WAITING; senão IN_PROGRESS.
// COMPLETED e CANCELLED só por ação manual.
public enum AmendmentStatus {
    IN_PROGRESS("Em andamento"),
    WAITING("Aguardando"),
    BLOCKED("Com impedimento"),
    COMPLETED("Concluída"),
    CANCELLED("Cancelada");

    private final String label;

    AmendmentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    // Concluída ou cancelada: fora do acompanhamento
    public boolean isClosed() {
        return this == COMPLETED || this == CANCELLED;
    }
}
