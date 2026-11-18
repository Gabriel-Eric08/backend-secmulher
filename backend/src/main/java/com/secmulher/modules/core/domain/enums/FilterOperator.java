package com.secmulher.modules.core.domain.enums;

import java.util.List;

import com.secmulher.modules.core.infra.entities.enums.FieldType;

// Operadores de filtro dos campos customizados. Cada tipo de campo aceita um subconjunto,
// devolvido em CategoryFieldResponse.filterOperators para o front montar a tela de filtros.
public enum FilterOperator {
    EQUALS,       // igual ao valor (texto: diferencia maiúsculas)
    NOT_EQUALS,   // diferente do valor (registros sem o campo também entram)
    CONTAINS,     // texto contém o valor, sem diferenciar maiúsculas
    STARTS_WITH,  // texto começa com o valor, sem diferenciar maiúsculas
    IN,           // valor é um dos itens da lista informada
    GT,           // maior que
    GTE,          // maior ou igual
    LT,           // menor que
    LTE,          // menor ou igual
    BETWEEN,      // entre [mínimo, máximo], inclusive
    IS_EMPTY,     // campo não preenchido (dispensa valor)
    NOT_EMPTY;    // campo preenchido (dispensa valor)

    private static final List<FilterOperator> TEXT = List.of(
        EQUALS, NOT_EQUALS, CONTAINS, STARTS_WITH, IN, IS_EMPTY, NOT_EMPTY);
    private static final List<FilterOperator> COMPARABLE = List.of(
        EQUALS, NOT_EQUALS, GT, GTE, LT, LTE, BETWEEN, IS_EMPTY, NOT_EMPTY);
    private static final List<FilterOperator> BOOLEAN = List.of(EQUALS, IS_EMPTY, NOT_EMPTY);
    private static final List<FilterOperator> LIST = List.of(EQUALS, NOT_EQUALS, IN, IS_EMPTY, NOT_EMPTY);

    public static List<FilterOperator> forType(FieldType type) {
        return switch (type) {
            case TEXT -> TEXT;
            case NUMBER, DATE -> COMPARABLE;
            case BOOLEAN -> BOOLEAN;
            case LIST -> LIST;
        };
    }

    public boolean needsValue() {
        return this != IS_EMPTY && this != NOT_EMPTY;
    }
}
