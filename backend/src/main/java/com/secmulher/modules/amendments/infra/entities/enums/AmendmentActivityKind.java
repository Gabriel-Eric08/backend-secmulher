package com.secmulher.modules.amendments.infra.entities.enums;

// Tipo de cada registro da linha do tempo da emenda
public enum AmendmentActivityKind {
    CREATED("Cadastro"),
    FIELD_UPDATE("Alteração de campos"),
    NOTE("Anotação"),
    PENDENCY_OPENED("Pendência aberta"),
    PENDENCY_RESOLVED("Pendência resolvida"),
    COMPLETED("Conclusão"),
    REOPENED("Reabertura"),
    CANCELLED("Cancelamento"),
    IMPORTED("Importação da planilha");

    private final String label;

    AmendmentActivityKind(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
