package com.secmulher.modules.core.infra.entities.enums;

// Ordem crescente de sigilo: quem acessa um nível acessa também os anteriores
public enum ConfidentialityLevel {
    INTERNAL,     // Público interno
    RESTRICTED,   // Restrito
    CONFIDENTIAL; // Sigiloso

    public boolean allows(ConfidentialityLevel level) {
        return level != null && level.ordinal() <= ordinal();
    }
}
