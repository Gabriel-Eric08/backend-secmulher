package com.secmulher.modules.fleet.infra.repositories;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.secmulher.modules.fleet.infra.entities.Trip;

public interface TripRepository extends JpaRepository<Trip, UUID>, JpaSpecificationExecutor<Trip> {

    boolean existsByDriverId(UUID driverId);

    boolean existsByVehicleId(UUID vehicleId);

    // Viagens não canceladas do motorista que se sobrepõem a [start, end), exceto a própria (excludeId)
    @Query("""
        select t from Trip t
        where t.driver.id = :driverId
          and t.status <> com.secmulher.modules.fleet.infra.entities.enums.TripStatus.CANCELLED
          and t.departureAt < :end and t.returnAt > :start
          and t.id <> :excludeId
        order by t.departureAt
        """)
    List<Trip> findDriverConflicts(UUID driverId, LocalDateTime start, LocalDateTime end, UUID excludeId);

    @Query("""
        select t from Trip t
        where t.vehicle.id = :vehicleId
          and t.status <> com.secmulher.modules.fleet.infra.entities.enums.TripStatus.CANCELLED
          and t.departureAt < :end and t.returnAt > :start
          and t.id <> :excludeId
        order by t.departureAt
        """)
    List<Trip> findVehicleConflicts(UUID vehicleId, LocalDateTime start, LocalDateTime end, UUID excludeId);

    // Motoristas e veículos ocupados em [start, end): para a consulta de disponibilidade
    @Query("""
        select distinct t.driver.id from Trip t
        where t.status <> com.secmulher.modules.fleet.infra.entities.enums.TripStatus.CANCELLED
          and t.departureAt < :end and t.returnAt > :start
        """)
    Collection<UUID> findBusyDriverIds(LocalDateTime start, LocalDateTime end);

    @Query("""
        select distinct t.vehicle.id from Trip t
        where t.status <> com.secmulher.modules.fleet.infra.entities.enums.TripStatus.CANCELLED
          and t.departureAt < :end and t.returnAt > :start
        """)
    Collection<UUID> findBusyVehicleIds(LocalDateTime start, LocalDateTime end);
}
