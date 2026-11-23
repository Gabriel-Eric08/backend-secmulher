package com.secmulher.modules.amendments.infra.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.secmulher.modules.amendments.infra.entities.AmendmentActivity;

public interface AmendmentActivityRepository extends JpaRepository<AmendmentActivity, UUID> {

    // Linha do tempo: mais recente primeiro
    List<AmendmentActivity> findByAmendmentIdOrderByOccurredAtDescCreatedAtDesc(UUID amendmentId);
}
