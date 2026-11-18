package com.secmulher.modules.finance.infra.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.secmulher.modules.finance.infra.entities.FinanceAction;

import jakarta.persistence.LockModeType;

public interface FinanceActionRepository extends JpaRepository<FinanceAction, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

    List<FinanceAction> findAllByOrderByCodeAsc();

    // Toda operação trava as ações envolvidas: os saldos da ação e das suas subações
    // só mudam com essa trava, então duas operações simultâneas não gastam o mesmo saldo
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from FinanceAction a where a.id = :id")
    Optional<FinanceAction> findByIdForUpdate(UUID id);
}
