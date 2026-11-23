package com.secmulher.modules.amendments.infra.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.secmulher.modules.amendments.infra.entities.AmendmentActivity;

public interface AmendmentActivityRepository extends JpaRepository<AmendmentActivity, UUID> {

    // Linha do tempo: mais recente primeiro
    @Query("""
        select a from AmendmentActivity a
        left join fetch a.user left join fetch a.pendency
        where a.amendment.id = :amendmentId
        order by a.occurredAt desc, a.createdAt desc
        """)
    List<AmendmentActivity> findTimeline(UUID amendmentId);
}
