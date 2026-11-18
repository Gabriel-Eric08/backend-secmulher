package com.secmulher.modules.finance.infra.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.secmulher.modules.finance.infra.entities.FinanceCommitment;

public interface FinanceCommitmentRepository extends JpaRepository<FinanceCommitment, UUID> {

    boolean existsByNumberIgnoreCase(String number);

    boolean existsByNumberIgnoreCaseAndIdNot(String number, UUID id);

    // Só o id da ação, sem carregar o empenho: ele é lido depois de travar a ação
    @Query("select c.action.id from FinanceCommitment c where c.id = :id")
    Optional<UUID> findActionIdById(UUID id);

    // Filtros opcionais; mais recentes primeiro (pela data do empenho)
    @Query("""
        select c from FinanceCommitment c
        join fetch c.subAction s
        join fetch c.action a
        where (:actionId is null or a.id = :actionId)
          and (:subActionId is null or s.id = :subActionId)
          and (:creditor is null or lower(c.creditor) like lower(concat('%', cast(:creditor as string), '%')))
        order by c.commitmentDate desc, c.createdAt desc
        """)
    List<FinanceCommitment> search(UUID actionId, UUID subActionId, String creditor);
}
