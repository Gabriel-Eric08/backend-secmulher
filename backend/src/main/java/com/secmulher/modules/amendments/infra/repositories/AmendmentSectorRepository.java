package com.secmulher.modules.amendments.infra.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.secmulher.modules.amendments.infra.entities.AmendmentSector;

public interface AmendmentSectorRepository extends JpaRepository<AmendmentSector, UUID> {

    List<AmendmentSector> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
