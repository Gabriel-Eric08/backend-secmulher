package com.secmulher.modules.fleet.domain.services;

import java.time.LocalDate;

import com.secmulher.modules.fleet.domain.dtos.DriverDtos.DriverResponse;
import com.secmulher.modules.fleet.domain.dtos.DriverDtos.DriverSummary;
import com.secmulher.modules.fleet.domain.dtos.VehicleDtos.VehicleResponse;
import com.secmulher.modules.fleet.domain.dtos.VehicleDtos.VehicleSummary;
import com.secmulher.modules.fleet.infra.entities.Driver;
import com.secmulher.modules.fleet.infra.entities.Vehicle;

final class FleetMapper {
    private FleetMapper() {}

    static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static DriverResponse driver(Driver driver) {
        return new DriverResponse(
            driver.getId(),
            driver.getName(),
            driver.getPhone(),
            driver.getLicenseNumber(),
            driver.getLicenseCategory(),
            driver.getLicenseExpiry(),
            driver.getLicenseExpiry().isBefore(LocalDate.now()),
            driver.getUser() != null ? driver.getUser().getId() : null,
            driver.getUser() != null ? driver.getUser().getName() : null,
            driver.getNotes(),
            driver.getIsActive(),
            driver.getCreatedAt(),
            driver.getUpdatedAt()
        );
    }

    static DriverSummary driverSummary(Driver driver) {
        return new DriverSummary(driver.getId(), driver.getName(), driver.getPhone());
    }

    static VehicleResponse vehicle(Vehicle vehicle) {
        return new VehicleResponse(
            vehicle.getId(),
            vehicle.getPlate(),
            vehicle.getBrand(),
            vehicle.getModel(),
            vehicle.getYear(),
            vehicle.getColor(),
            vehicle.getCapacity(),
            vehicle.getOdometerKm(),
            vehicle.getNotes(),
            vehicle.getIsActive(),
            vehicle.getCreatedAt(),
            vehicle.getUpdatedAt()
        );
    }

    static VehicleSummary vehicleSummary(Vehicle vehicle) {
        return new VehicleSummary(vehicle.getId(), vehicle.getPlate(), vehicle.getBrand(), vehicle.getModel(),
            vehicle.getCapacity());
    }
}
