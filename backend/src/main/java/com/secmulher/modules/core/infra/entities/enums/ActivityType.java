package com.secmulher.modules.core.infra.entities.enums;

public enum ActivityType {
    // Registro manual: o tipo de fato (ex: 'Atendimento psicológico') vem de activity_types,
    // configurado por categoria
    MANUAL(true),

    // Tipos manuais antigos (enum fixo). Registros migrados viram MANUAL; os que não puderam ser
    // migrados (ex: registro já excluído) continuam legíveis. Na API, ainda aceitos como atalho para
    // o tipo padrão correspondente da categoria (triagem, atendimento, acompanhamento, anotacao).
    @Deprecated TRIAGE(true),
    @Deprecated SERVICE(true),
    @Deprecated FOLLOW_UP(true),
    @Deprecated NOTE(true),

    // Gerados automaticamente pelo sistema (auditoria)
    CREATED(false),
    UPDATED(false),
    DELETED(false),
    FILE_UPLOADED(false),
    FILE_UPDATED(false),
    FILE_REMOVED(false),
    FILE_DOWNLOADED(false);

    private final boolean manual;

    ActivityType(boolean manual) {
        this.manual = manual;
    }

    public boolean isManual() {
        return manual;
    }

    // Key do tipo padrão equivalente a um valor antigo do enum; null se não houver
    public String legacyDefaultKey() {
        return switch (this) {
            case TRIAGE -> "triagem";
            case SERVICE -> "atendimento";
            case FOLLOW_UP -> "acompanhamento";
            case NOTE -> "anotacao";
            default -> null;
        };
    }
}
