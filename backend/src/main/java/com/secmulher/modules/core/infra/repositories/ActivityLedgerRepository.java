package com.secmulher.modules.core.infra.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.secmulher.modules.core.infra.entities.ActivityLedger;

public interface ActivityLedgerRepository extends JpaRepository<ActivityLedger, UUID> {
    List<ActivityLedger> findByEntityTypeAndEntityIdOrderByOccurredAtDescCreatedAtDesc(String entityType, UUID entityId);

    List<ActivityLedger> findByUserIdOrderByCreatedAtDesc(UUID userId);

    boolean existsByActivityTypeId(UUID activityTypeId);

    boolean existsByActivityType_Category_Id(UUID categoryId);
}
