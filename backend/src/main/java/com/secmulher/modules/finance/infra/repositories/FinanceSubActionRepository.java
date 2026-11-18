package com.secmulher.modules.finance.infra.repositories;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.secmulher.modules.finance.infra.entities.FinanceSubAction;

public interface FinanceSubActionRepository extends JpaRepository<FinanceSubAction, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

    List<FinanceSubAction> findAllByOrderByCodeAsc();

    List<FinanceSubAction> findByActionIdOrderByCodeAsc(UUID actionId);

    @Query("select coalesce(sum(s.balance), 0) from FinanceSubAction s where s.action.id = :actionId")
    BigDecimal sumBalanceByActionId(UUID actionId);

    // Só o id da ação, sem carregar a subação: ela é lida depois de travar a ação
    @Query("select s.action.id from FinanceSubAction s where s.id = :id")
    Optional<UUID> findActionIdById(UUID id);
}
