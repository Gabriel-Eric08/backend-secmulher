package com.secmulher.modules.amendments.infra.entities.enums;

// Tipo da pendência de uma emenda
public enum PendencyKind {
    IMPEDIMENT("Impedimento"),
    WAITING("Aguardando");

    private final String label;

    PendencyKind(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
