package com.secmulher.modules.fleet.infra.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.secmulher.modules.fleet.infra.entities.Driver;

import jakarta.persistence.LockModeType;

public interface DriverRepository extends JpaRepository<Driver, UUID> {

    List<Driver> findAllByOrderByNameAsc();

    boolean existsByLicenseNumber(String licenseNumber);

    boolean existsByLicenseNumberAndIdNot(String licenseNumber, UUID id);

    boolean existsByUserId(UUID userId);

    boolean existsByUserIdAndIdNot(UUID userId, UUID id);

    Optional<Driver> findByUserId(UUID userId);

    // Escalar uma viagem trava o motorista, para duas escalas simultâneas não se sobreporem
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Driver d where d.id = :id")
    Optional<Driver> findByIdForUpdate(UUID id);
}
