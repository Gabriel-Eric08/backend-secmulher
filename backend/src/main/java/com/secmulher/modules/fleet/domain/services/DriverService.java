package com.secmulher.modules.fleet.domain.services;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.modules.core.domain.exceptions.ConflictException;
import com.secmulher.modules.core.domain.exceptions.NotFoundException;
import com.secmulher.modules.core.infra.entities.User;
import com.secmulher.modules.core.infra.repositories.UserRepository;
import com.secmulher.modules.fleet.domain.access.FleetAccessService;
import com.secmulher.modules.fleet.domain.dtos.DriverDtos.DriverRequest;
import com.secmulher.modules.fleet.domain.dtos.DriverDtos.DriverResponse;
import com.secmulher.modules.fleet.infra.entities.Driver;
import com.secmulher.modules.fleet.infra.repositories.DriverRepository;
import com.secmulher.modules.fleet.infra.repositories.TripRepository;

// Cadastro de motoristas. CNH única; cada usuário do sistema pode estar vinculado a no máximo um motorista
@Service
public class DriverService {
    final DriverRepository driverRepository;
    final TripRepository tripRepository;
    final UserRepository userRepository;
    final FleetAccessService access;

    public DriverService(DriverRepository driverRepository, TripRepository tripRepository, UserRepository userRepository,
            FleetAccessService access) {
        this.driverRepository = driverRepository;
        this.tripRepository = tripRepository;
        this.userRepository = userRepository;
        this.access = access;
    }

    Driver getDriverById(UUID id) {
        return driverRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Motorista não encontrado: " + id));
    }

    @Transactional(readOnly = true)
    public List<DriverResponse> findAll() {
        access.requireRead();
        return driverRepository.findAllByOrderByNameAsc().stream().map(FleetMapper::driver).toList();
    }

    @Transactional(readOnly = true)
    public DriverResponse findById(UUID id) {
        access.requireRead();
        return FleetMapper.driver(getDriverById(id));
    }

    @Transactional
    public DriverResponse create(DriverRequest request) {
        User createdBy = access.requireCreate();
        if (driverRepository.existsByLicenseNumber(request.licenseNumber())) {
            throw new ConflictException("Já existe um motorista com a CNH " + request.licenseNumber());
        }
        Driver driver = Driver.builder()
            .createdBy(createdBy)
            .isActive(request.isActive() == null || request.isActive())
            .build();
        apply(driver, request, null);
        return FleetMapper.driver(driverRepository.saveAndFlush(driver));
    }

    @Transactional
    public DriverResponse update(UUID id, DriverRequest request) {
        access.requireUpdate();
        Driver driver = getDriverById(id);
        if (driverRepository.existsByLicenseNumberAndIdNot(request.licenseNumber(), id)) {
            throw new ConflictException("Já existe um motorista com a CNH " + request.licenseNumber());
        }
        apply(driver, request, id);
        if (request.isActive() != null) {
            driver.setIsActive(request.isActive());
        }
        return FleetMapper.driver(driverRepository.saveAndFlush(driver));
    }

    // Só sai do banco se nunca foi escalado; caso contrário, desative com isActive: false
    @Transactional
    public void delete(UUID id) {
        access.requireDelete();
        Driver driver = getDriverById(id);
        if (tripRepository.existsByDriverId(id)) {
            throw new ConflictException("O motorista " + driver.getName() + " tem viagens e não pode ser excluído. "
                + "Desative-o com isActive: false");
        }
        driverRepository.delete(driver);
    }

    private void apply(Driver driver, DriverRequest request, UUID currentId) {
        User user = null;
        if (request.userId() != null) {
            user = userRepository.findById(request.userId())
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado: " + request.userId()));
            boolean taken = currentId == null
                ? driverRepository.existsByUserId(user.getId())
                : driverRepository.existsByUserIdAndIdNot(user.getId(), currentId);
            if (taken) {
                throw new ConflictException("O usuário " + user.getName() + " já está vinculado a outro motorista");
            }
        }
        driver.setName(request.name().trim());
        driver.setPhone(FleetMapper.normalize(request.phone()));
        driver.setLicenseNumber(request.licenseNumber());
        driver.setLicenseCategory(request.licenseCategory());
        driver.setLicenseExpiry(request.licenseExpiry());
        driver.setUser(user);
        driver.setNotes(FleetMapper.normalize(request.notes()));
    }
}
