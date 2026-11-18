package com.secmulher.modules.fleet.domain.services;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.modules.core.domain.exceptions.ConflictException;
import com.secmulher.modules.core.domain.exceptions.NotFoundException;
import com.secmulher.modules.core.infra.entities.User;
import com.secmulher.modules.fleet.domain.access.FleetAccessService;
import com.secmulher.modules.fleet.domain.dtos.VehicleDtos.VehicleRequest;
import com.secmulher.modules.fleet.domain.dtos.VehicleDtos.VehicleResponse;
import com.secmulher.modules.fleet.infra.entities.Vehicle;
import com.secmulher.modules.fleet.infra.repositories.TripRepository;
import com.secmulher.modules.fleet.infra.repositories.VehicleRepository;

// Cadastro de veículos. Placa única, gravada sem hífen e em maiúsculas
@Service
public class VehicleService {
    final VehicleRepository vehicleRepository;
    final TripRepository tripRepository;
    final FleetAccessService access;

    public VehicleService(VehicleRepository vehicleRepository, TripRepository tripRepository, FleetAccessService access) {
        this.vehicleRepository = vehicleRepository;
        this.tripRepository = tripRepository;
        this.access = access;
    }

    static String normalizePlate(String plate) {
        return plate.trim().replace("-", "").toUpperCase(Locale.ROOT);
    }

    Vehicle getVehicleById(UUID id) {
        return vehicleRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Veículo não encontrado: " + id));
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> findAll() {
        access.requireRead();
        return vehicleRepository.findAllByOrderByPlateAsc().stream().map(FleetMapper::vehicle).toList();
    }

    @Transactional(readOnly = true)
    public VehicleResponse findById(UUID id) {
        access.requireRead();
        return FleetMapper.vehicle(getVehicleById(id));
    }

    @Transactional
    public VehicleResponse create(VehicleRequest request) {
        User createdBy = access.requireCreate();
        String plate = normalizePlate(request.plate());
        if (vehicleRepository.existsByPlate(plate)) {
            throw new ConflictException("Já existe um veículo com a placa " + plate);
        }
        Vehicle vehicle = Vehicle.builder()
            .createdBy(createdBy)
            .odometerKm(request.odometerKm() != null ? request.odometerKm() : 0)
            .isActive(request.isActive() == null || request.isActive())
            .build();
        apply(vehicle, request, plate);
        return FleetMapper.vehicle(vehicleRepository.saveAndFlush(vehicle));
    }

    // odometerKm pode ser corrigido aqui (ex.: ajuste após manutenção); viagens concluídas também o avançam
    @Transactional
    public VehicleResponse update(UUID id, VehicleRequest request) {
        access.requireUpdate();
        Vehicle vehicle = getVehicleById(id);
        String plate = normalizePlate(request.plate());
        if (vehicleRepository.existsByPlateAndIdNot(plate, id)) {
            throw new ConflictException("Já existe um veículo com a placa " + plate);
        }
        apply(vehicle, request, plate);
        if (request.odometerKm() != null) {
            vehicle.setOdometerKm(request.odometerKm());
        }
        if (request.isActive() != null) {
            vehicle.setIsActive(request.isActive());
        }
        return FleetMapper.vehicle(vehicleRepository.saveAndFlush(vehicle));
    }

    @Transactional
    public void delete(UUID id) {
        access.requireDelete();
        Vehicle vehicle = getVehicleById(id);
        if (tripRepository.existsByVehicleId(id)) {
            throw new ConflictException("O veículo " + vehicle.getPlate() + " tem viagens e não pode ser excluído. "
                + "Desative-o com isActive: false");
        }
        vehicleRepository.delete(vehicle);
    }

    private static void apply(Vehicle vehicle, VehicleRequest request, String plate) {
        vehicle.setPlate(plate);
        vehicle.setBrand(FleetMapper.normalize(request.brand()));
        vehicle.setModel(request.model().trim());
        vehicle.setYear(request.year());
        vehicle.setColor(FleetMapper.normalize(request.color()));
        vehicle.setCapacity(request.capacity());
        vehicle.setNotes(FleetMapper.normalize(request.notes()));
    }
}
