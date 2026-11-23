package com.secmulher.modules.amendments.infra.repositories;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.secmulher.modules.amendments.infra.entities.AmendmentPendency;
import com.secmulher.modules.amendments.infra.entities.enums.PendencyKind;

public interface AmendmentPendencyRepository extends JpaRepository<AmendmentPendency, UUID> {

    @Query("""
        select p from AmendmentPendency p
        left join fetch p.openedBy left join fetch p.resolvedBy
        where p.amendment.id = :amendmentId
        order by p.openedAt desc
        """)
    List<AmendmentPendency> findByAmendmentId(UUID amendmentId);

    // Pendências abertas da emenda, da mais antiga para a mais nova
    List<AmendmentPendency> findByAmendmentIdAndResolvedAtIsNullOrderByOpenedAtAsc(UUID amendmentId);

    // Pendências abertas de várias emendas (listagem), da mais antiga para a mais nova
    @Query("""
        select p from AmendmentPendency p
        where p.amendment.id in :amendmentIds and p.resolvedAt is null
        order by p.openedAt asc
        """)
    List<AmendmentPendency> findOpenByAmendmentIds(Collection<UUID> amendmentIds);

    boolean existsByAmendmentIdAndResolvedAtIsNull(UUID amendmentId);

    boolean existsByAmendmentIdAndKindAndResolvedAtIsNull(UUID amendmentId, PendencyKind kind);
}
