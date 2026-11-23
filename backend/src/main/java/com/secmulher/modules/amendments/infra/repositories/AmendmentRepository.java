package com.secmulher.modules.amendments.infra.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.secmulher.modules.amendments.infra.entities.Amendment;

public interface AmendmentRepository extends JpaRepository<Amendment, UUID>, JpaSpecificationExecutor<Amendment> {

    // Chave da importação: código único no ano, sem diferenciar maiúsculas
    @Query("select a from Amendment a where a.exerciseYear = :exerciseYear and lower(a.code) = lower(:code)")
    Optional<Amendment> findByYearAndCode(Integer exerciseYear, String code);

    boolean existsBySectorId(UUID sectorId);
}
