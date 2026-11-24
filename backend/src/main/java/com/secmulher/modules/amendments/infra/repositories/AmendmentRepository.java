package com.secmulher.modules.amendments.infra.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.secmulher.modules.amendments.infra.entities.Amendment;

import jakarta.persistence.LockModeType;

public interface AmendmentRepository extends JpaRepository<Amendment, UUID>, JpaSpecificationExecutor<Amendment> {

    // Chave da importação: código único no ano, sem diferenciar maiúsculas
    @Query("select a from Amendment a where a.exerciseYear = :exerciseYear and lower(a.code) = lower(:code)")
    Optional<Amendment> findByYearAndCode(Integer exerciseYear, String code);

    boolean existsBySectorId(UUID sectorId);

    // Atividades e pendências travam a emenda, para duas técnicas não recalcularem a situação ao mesmo tempo
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Amendment a where a.id = :id")
    Optional<Amendment> findByIdForUpdate(UUID id);

    // Valores já usados, para os filtros do front. Escopo: restricted = true limita às emendas do setor
    // (sectorId) ou sob responsabilidade do usuário (userId); false = todas
    String SCOPE = " (:restricted = false or a.sector.id = :sectorId or a.responsibleUser.id = :userId) ";

    @Query("select distinct a.exerciseYear from Amendment a where" + SCOPE + "order by a.exerciseYear desc")
    List<Integer> findExerciseYears(boolean restricted, UUID sectorId, UUID userId);

    @Query("select distinct a.cycle from Amendment a where a.cycle is not null and" + SCOPE + "order by a.cycle")
    List<String> findCycles(boolean restricted, UUID sectorId, UUID userId);

    @Query("select distinct a.parliamentarian from Amendment a where" + SCOPE + "order by a.parliamentarian")
    List<String> findParliamentarians(boolean restricted, UUID sectorId, UUID userId);

    @Query("select distinct a.municipality from Amendment a where a.municipality is not null and" + SCOPE
        + "order by a.municipality")
    List<String> findMunicipalities(boolean restricted, UUID sectorId, UUID userId);

    @Query("select distinct a.category from Amendment a where a.category is not null and" + SCOPE + "order by a.category")
    List<String> findCategories(boolean restricted, UUID sectorId, UUID userId);

    @Query("select distinct a.type from Amendment a where a.type is not null and" + SCOPE + "order by a.type")
    List<String> findTypes(boolean restricted, UUID sectorId, UUID userId);

    @Query("select distinct a.technicalArea from Amendment a where a.technicalArea is not null and" + SCOPE
        + "order by a.technicalArea")
    List<String> findTechnicalAreas(boolean restricted, UUID sectorId, UUID userId);
}
