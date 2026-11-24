package com.secmulher.modules.amendments.infra.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.secmulher.modules.amendments.infra.entities.AmendmentSectorMember;

public interface AmendmentSectorMemberRepository extends JpaRepository<AmendmentSectorMember, UUID> {

    @Query("select m from AmendmentSectorMember m join fetch m.sector where m.user.id = :userId")
    Optional<AmendmentSectorMember> findByUserId(UUID userId);

    @Query("select m from AmendmentSectorMember m join fetch m.user u where m.sector.id = :sectorId order by lower(u.name)")
    List<AmendmentSectorMember> findBySectorId(UUID sectorId);
}
