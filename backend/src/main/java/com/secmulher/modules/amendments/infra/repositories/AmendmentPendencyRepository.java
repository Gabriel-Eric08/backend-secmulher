package com.secmulher.modules.amendments.infra.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.secmulher.modules.amendments.infra.entities.AmendmentPendency;
import com.secmulher.modules.amendments.infra.entities.enums.PendencyKind;

public interface AmendmentPendencyRepository extends JpaRepository<AmendmentPendency, UUID> {

    List<AmendmentPendency> findByAmendmentIdOrderByOpenedAtDesc(UUID amendmentId);

    // Pendências abertas da emenda, da mais antiga para a mais nova
    List<AmendmentPendency> findByAmendmentIdAndResolvedAtIsNullOrderByOpenedAtAsc(UUID amendmentId);

    boolean existsByAmendmentIdAndResolvedAtIsNull(UUID amendmentId);

    boolean existsByAmendmentIdAndKindAndResolvedAtIsNull(UUID amendmentId, PendencyKind kind);
}
