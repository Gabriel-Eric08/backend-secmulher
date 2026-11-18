package com.secmulher.modules.finance.infra.repositories;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.secmulher.modules.finance.infra.entities.FinanceEntry;

public interface FinanceEntryRepository extends JpaRepository<FinanceEntry, UUID> {

    boolean existsByActionId(UUID actionId);

    boolean existsBySubActionId(UUID subActionId);

    @Query("""
        select e from FinanceEntry e
        join fetch e.action
        left join fetch e.subAction
        where e.operation.id in :operationIds
        """)
    List<FinanceEntry> findWithDetailsByOperationIds(Collection<UUID> operationIds);

    // Todos os lançamentos que tocam a ação (no total ou em qualquer subação dela), em ordem cronológica
    // pela data real; no mesmo dia, pela ordem de registro
    @Query("""
        select e from FinanceEntry e
        join fetch e.operation o
        left join fetch e.subAction
        where e.action.id = :actionId
        order by o.operationDate, o.number
        """)
    List<FinanceEntry> findByActionIdInOrder(UUID actionId);

    @Query("""
        select e from FinanceEntry e
        join fetch e.operation o
        where e.subAction.id = :subActionId
        order by o.operationDate, o.number
        """)
    List<FinanceEntry> findBySubActionIdInOrder(UUID subActionId);

    // Valores (dotação e execução) de todas as ações e subações no fim de um dia, pela data real
    @Query("""
        select new com.secmulher.modules.finance.infra.repositories.FinanceBalanceRow(e.action.id, s.id, e.kind, sum(e.amount))
        from FinanceEntry e
        left join e.subAction s
        where e.operation.operationDate <= :date
        group by e.action.id, s.id, e.kind
        """)
    List<FinanceBalanceRow> sumBalancesUntil(LocalDate date);
}
