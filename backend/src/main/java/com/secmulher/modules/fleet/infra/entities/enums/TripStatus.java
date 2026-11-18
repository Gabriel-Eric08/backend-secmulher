package com.secmulher.modules.fleet.infra.entities.enums;

// Ciclo de vida da viagem: SCHEDULED -> IN_PROGRESS -> COMPLETED, ou SCHEDULED -> CANCELLED
public enum TripStatus {
    SCHEDULED("Agendada"),
    IN_PROGRESS("Em andamento"),
    COMPLETED("Concluída"),
    CANCELLED("Cancelada");

    private final String label;

    TripStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
