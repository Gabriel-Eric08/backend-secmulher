package com.secmulher.modules.core.domain.exceptions;

import java.util.Map;

// Erros de validação dos campos customizáveis, indexados pela chave do campo
public class FieldValidationException extends RuntimeException {
    private final Map<String, String> errors;

    public FieldValidationException(Map<String, String> errors) {
        this("Campos customizados inválidos", errors);
    }

    public FieldValidationException(String message, Map<String, String> errors) {
        super(message);
        this.errors = errors;
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
