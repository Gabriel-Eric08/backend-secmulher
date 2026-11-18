package com.secmulher.modules.core.domain.enums;

import java.util.Arrays;

import com.secmulher.modules.core.domain.exceptions.NotFoundException;

// Tipos de registro que podem ter linha do tempo e documentos (colunas polimórficas entity_type)
public enum RecordType {
    CLIENT("clients", "clients"),
    DYNAMIC_ENTITY("dynamic-entities", "dynamic_entities");

    private final String path;
    private final String tableName;

    RecordType(String path, String tableName) {
        this.path = path;
        this.tableName = tableName;
    }

    public String getPath() {
        return path;
    }

    public String getTableName() {
        return tableName;
    }

    public static RecordType fromPath(String path) {
        return Arrays.stream(values())
            .filter(type -> type.path.equals(path))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Tipo de registro desconhecido: " + path));
    }

    public static RecordType fromTableName(String tableName) {
        return Arrays.stream(values())
            .filter(type -> type.tableName.equals(tableName))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Tabela polimórfica desconhecida: " + tableName));
    }
}
