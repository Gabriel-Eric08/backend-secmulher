package com.secmulher.modules.amendments.domain.access;

import java.util.Objects;
import java.util.UUID;

import com.secmulher.modules.amendments.infra.entities.Amendment;
import com.secmulher.modules.amendments.infra.entities.AmendmentSector;

// Quais emendas o usuário atual alcança. sector nulo = todas (administrador ou usuário sem setor);
// com setor = as emendas do setor e as que estão sob responsabilidade do próprio usuário.
public record AmendmentScope(UUID userId, AmendmentSector sector) {

    public boolean restricted() {
        return sector != null;
    }

    public UUID sectorId() {
        return sector != null ? sector.getId() : null;
    }

    public boolean covers(Amendment amendment) {
        if (!restricted()) {
            return true;
        }
        boolean sameSector = amendment.getSector() != null && Objects.equals(amendment.getSector().getId(), sector.getId());
        boolean responsible = amendment.getResponsibleUser() != null
            && Objects.equals(amendment.getResponsibleUser().getId(), userId);
        return sameSector || responsible;
    }
}
