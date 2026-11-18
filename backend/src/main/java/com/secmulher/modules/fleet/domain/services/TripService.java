package com.secmulher.modules.fleet.domain.services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secmulher.modules.core.domain.exceptions.BusinessException;
import com.secmulher.modules.core.domain.exceptions.ConflictException;
import com.secmulher.modules.core.domain.exceptions.FieldValidationException;
import com.secmulher.modules.core.domain.exceptions.ForbiddenException;
import com.secmulher.modules.core.domain.exceptions.NotFoundException;
import com.secmulher.modules.core.infra.entities.Client;
import com.secmulher.modules.core.infra.entities.User;
import com.secmulher.modules.core.infra.repositories.ClientRepository;
import com.secmulher.modules.core.infra.repositories.UserRepository;
import com.secmulher.modules.fleet.domain.access.FleetAccessService;
import com.secmulher.modules.fleet.domain.dtos.TripDtos.AvailabilityResponse;
import com.secmulher.modules.fleet.domain.dtos.TripDtos.CancelTripRequest;
import com.secmulher.modules.fleet.domain.dtos.TripDtos.CompleteTripRequest;
import com.secmulher.modules.fleet.domain.dtos.TripDtos.ParticipantRequest;
import com.secmulher.modules.fleet.domain.dtos.TripDtos.ParticipantResponse;
import com.secmulher.modules.fleet.domain.dtos.TripDtos.StartTripRequest;
import com.secmulher.modules.fleet.domain.dtos.TripDtos.TripRequest;
import com.secmulher.modules.fleet.domain.dtos.TripDtos.TripResponse;
import com.secmulher.modules.fleet.infra.entities.Driver;
import com.secmulher.modules.fleet.infra.entities.Trip;
import com.secmulher.modules.fleet.infra.entities.TripParticipant;
import com.secmulher.modules.fleet.infra.entities.Vehicle;
import com.secmulher.modules.fleet.infra.entities.enums.ParticipantType;
import com.secmulher.modules.fleet.infra.entities.enums.TripStatus;
import com.secmulher.modules.fleet.infra.repositories.DriverRepository;
import com.secmulher.modules.fleet.infra.repositories.TripRepository;
import com.secmulher.modules.fleet.infra.repositories.VehicleRepository;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

// Viagens e agenda. Regras de escala (na criação e na edição, com motorista e veículo travados):
//   - volta depois da ida;
//   - motorista e veículo ativos;
//   - CNH do motorista válida até o dia da volta;
//   - nenhuma outra viagem não cancelada do mesmo motorista ou do mesmo veículo no intervalo (409);
//   - participantes dentro da capacidade do veículo.
// Ciclo de vida: SCHEDULED -> (start) IN_PROGRESS -> (complete) COMPLETED; SCHEDULED -> (cancel) CANCELLED.
// Só viagens agendadas podem ser editadas.
@Service
public class TripService {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    // Para consultas de conflito na criação: um id que nunca existe
    private static final UUID NO_TRIP = new UUID(0L, 0L);

    final TripRepository tripRepository;
    final DriverRepository driverRepository;
    final VehicleRepository vehicleRepository;
    final UserRepository userRepository;
    final ClientRepository clientRepository;
    final FleetAccessService access;

    public TripService(TripRepository tripRepository, DriverRepository driverRepository,
            VehicleRepository vehicleRepository, UserRepository userRepository, ClientRepository clientRepository,
            FleetAccessService access) {
        this.tripRepository = tripRepository;
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
        this.clientRepository = clientRepository;
        this.access = access;
    }

    private Trip getTripById(UUID id) {
        return tripRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Viagem não encontrada: " + id));
    }

    // ---------------------------------------------------------------- consultas

    // Viagens que se sobrepõem ao período [from, to]; todos os filtros são opcionais
    @Transactional(readOnly = true)
    public List<TripResponse> search(LocalDate from, LocalDate to, UUID driverId, UUID vehicleId, TripStatus status) {
        access.requireRead();
        return toResponses(tripRepository.findAll(spec(from, to, driverId, vehicleId, status),
            Sort.by("departureAt", "number")));
    }

    // Agenda do motorista vinculado ao usuário logado (não exige permissão na categoria)
    @Transactional(readOnly = true)
    public List<TripResponse> mine(LocalDate from, LocalDate to) {
        User user = access.requireUser();
        Driver driver = driverRepository.findByUserId(user.getId())
            .orElseThrow(() -> new NotFoundException("Seu usuário não está vinculado a um motorista"));
        return toResponses(tripRepository.findAll(spec(from, to, driver.getId(), null, null),
            Sort.by("departureAt", "number")));
    }

    // Quem tem leitura vê qualquer viagem; o motorista vinculado vê as suas
    @Transactional(readOnly = true)
    public TripResponse findById(UUID id) {
        Trip trip = getTripById(id);
        if (!access.access().canRead()) {
            User user = access.requireUser();
            User driverUser = trip.getDriver().getUser();
            if (driverUser == null || !driverUser.getId().equals(user.getId())) {
                throw new ForbiddenException("Sem permissão de leitura nesta categoria");
            }
        }
        return toResponse(trip, access.canSeeClientNames());
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse availability(LocalDateTime start, LocalDateTime end) {
        access.requireRead();
        requirePeriod(start, end);
        Collection<UUID> busyDrivers = new HashSet<>(tripRepository.findBusyDriverIds(start, end));
        Collection<UUID> busyVehicles = new HashSet<>(tripRepository.findBusyVehicleIds(start, end));
        return new AvailabilityResponse(start, end,
            driverRepository.findAllByOrderByNameAsc().stream()
                .filter(driver -> Boolean.TRUE.equals(driver.getIsActive()))
                .filter(driver -> !driver.getLicenseExpiry().isBefore(end.toLocalDate()))
                .filter(driver -> !busyDrivers.contains(driver.getId()))
                .map(FleetMapper::driver).toList(),
            vehicleRepository.findAllByOrderByPlateAsc().stream()
                .filter(vehicle -> Boolean.TRUE.equals(vehicle.getIsActive()))
                .filter(vehicle -> !busyVehicles.contains(vehicle.getId()))
                .map(FleetMapper::vehicle).toList());
    }

    // ---------------------------------------------------------------- cadastro

    @Transactional
    public TripResponse create(TripRequest request) {
        User user = access.requireCreate();
        requirePeriod(request.departureAt(), request.returnAt());
        Driver driver = lockDriver(request.driverId());
        Vehicle vehicle = lockVehicle(request.vehicleId());
        requireSchedulable(driver, vehicle, request.departureAt(), request.returnAt(), NO_TRIP);

        Trip trip = Trip.builder()
            .status(TripStatus.SCHEDULED)
            .createdBy(user)
            .build();
        applyDetails(trip, request, driver, vehicle);
        addParticipants(trip, request.participants(), vehicle);
        return toResponse(tripRepository.saveAndFlush(trip), access.canSeeClientNames());
    }

    @Transactional
    public TripResponse update(UUID id, TripRequest request) {
        access.requireUpdate();
        Trip trip = getTripById(id);
        requireStatus(trip, TripStatus.SCHEDULED, "editada");
        requirePeriod(request.departureAt(), request.returnAt());
        Driver driver = lockDriver(request.driverId());
        Vehicle vehicle = lockVehicle(request.vehicleId());
        requireSchedulable(driver, vehicle, request.departureAt(), request.returnAt(), trip.getId());

        applyDetails(trip, request, driver, vehicle);
        // A lista enviada substitui a anterior; o flush remove as antigas antes de inserir as novas
        trip.getParticipants().clear();
        tripRepository.flush();
        addParticipants(trip, request.participants(), vehicle);
        return toResponse(tripRepository.saveAndFlush(trip), access.canSeeClientNames());
    }

    // ---------------------------------------------------------------- ciclo de vida

    @Transactional
    public TripResponse start(UUID id, StartTripRequest request) {
        access.requireUpdate();
        Trip trip = getTripById(id);
        requireStatus(trip, TripStatus.SCHEDULED, "iniciada");
        trip.setStatus(TripStatus.IN_PROGRESS);
        trip.setActualDepartureAt(request.actualDepartureAt() != null ? request.actualDepartureAt() : LocalDateTime.now());
        trip.setStartOdometerKm(request.startOdometerKm());
        return toResponse(tripRepository.saveAndFlush(trip), access.canSeeClientNames());
    }

    // Conclui a viagem e avança o odômetro do veículo (nunca o faz voltar)
    @Transactional
    public TripResponse complete(UUID id, CompleteTripRequest request) {
        access.requireUpdate();
        Trip trip = getTripById(id);
        requireStatus(trip, TripStatus.IN_PROGRESS, "concluída");
        if (request.endOdometerKm() < trip.getStartOdometerKm()) {
            throw new BusinessException("A quilometragem final (" + request.endOdometerKm()
                + " km) não pode ser menor que a inicial (" + trip.getStartOdometerKm() + " km)");
        }
        LocalDateTime actualReturn = request.actualReturnAt() != null ? request.actualReturnAt() : LocalDateTime.now();
        if (actualReturn.isBefore(trip.getActualDepartureAt())) {
            throw new BusinessException("A volta (" + WHEN.format(actualReturn) + ") não pode ser antes da saída ("
                + WHEN.format(trip.getActualDepartureAt()) + ")");
        }

        Vehicle vehicle = lockVehicle(trip.getVehicle().getId());
        trip.setStatus(TripStatus.COMPLETED);
        trip.setActualReturnAt(actualReturn);
        trip.setEndOdometerKm(request.endOdometerKm());
        trip.setCompletionNotes(FleetMapper.normalize(request.notes()));
        if (request.endOdometerKm() > vehicle.getOdometerKm()) {
            vehicle.setOdometerKm(request.endOdometerKm());
        }
        return toResponse(tripRepository.saveAndFlush(trip), access.canSeeClientNames());
    }

    @Transactional
    public TripResponse cancel(UUID id, CancelTripRequest request) {
        User user = access.requireUpdate();
        Trip trip = getTripById(id);
        requireStatus(trip, TripStatus.SCHEDULED, "cancelada");
        trip.setStatus(TripStatus.CANCELLED);
        trip.setCancellationReason(request.reason().trim());
        trip.setCancelledAt(LocalDateTime.now());
        trip.setCancelledBy(user);
        return toResponse(tripRepository.saveAndFlush(trip), access.canSeeClientNames());
    }

    // ---------------------------------------------------------------- regras

    private static void requirePeriod(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new FieldValidationException("Dados inválidos", Map.of("returnAt", "deve ser depois da ida"));
        }
    }

    private static void requireStatus(Trip trip, TripStatus expected, String action) {
        if (trip.getStatus() != expected) {
            throw new BusinessException("A viagem nº " + trip.getNumber() + " está " + trip.getStatus().getLabel()
                .toLowerCase() + " e não pode ser " + action + " (precisa estar " + expected.getLabel().toLowerCase() + ")");
        }
    }

    // Motorista antes do veículo, sempre: a mesma ordem de trava em todas as operações evita deadlock
    private Driver lockDriver(UUID id) {
        return driverRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new NotFoundException("Motorista não encontrado: " + id));
    }

    private Vehicle lockVehicle(UUID id) {
        return vehicleRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new NotFoundException("Veículo não encontrado: " + id));
    }

    private void requireSchedulable(Driver driver, Vehicle vehicle, LocalDateTime start, LocalDateTime end, UUID tripId) {
        if (!Boolean.TRUE.equals(driver.getIsActive())) {
            throw new BusinessException("O motorista " + driver.getName() + " está inativo");
        }
        if (!Boolean.TRUE.equals(vehicle.getIsActive())) {
            throw new BusinessException("O veículo " + vehicle.getPlate() + " está inativo");
        }
        if (driver.getLicenseExpiry().isBefore(end.toLocalDate())) {
            throw new BusinessException("A CNH de " + driver.getName() + " vence em " + DAY.format(driver.getLicenseExpiry())
                + ", antes da volta da viagem (" + DAY.format(end.toLocalDate()) + ")");
        }
        List<Trip> driverConflicts = tripRepository.findDriverConflicts(driver.getId(), start, end, tripId);
        if (!driverConflicts.isEmpty()) {
            throw new ConflictException("O motorista " + driver.getName() + " já está na " + describe(driverConflicts.getFirst()));
        }
        List<Trip> vehicleConflicts = tripRepository.findVehicleConflicts(vehicle.getId(), start, end, tripId);
        if (!vehicleConflicts.isEmpty()) {
            throw new ConflictException("O veículo " + vehicle.getPlate() + " já está na " + describe(vehicleConflicts.getFirst()));
        }
    }

    private static String describe(Trip trip) {
        return "viagem nº " + trip.getNumber() + " (" + trip.getPurpose() + "), de " + WHEN.format(trip.getDepartureAt())
            + " a " + WHEN.format(trip.getReturnAt());
    }

    private static void applyDetails(Trip trip, TripRequest request, Driver driver, Vehicle vehicle) {
        trip.setDriver(driver);
        trip.setVehicle(vehicle);
        trip.setPurpose(request.purpose().trim());
        trip.setDescription(FleetMapper.normalize(request.description()));
        trip.setDepartureLocation(request.departureLocation().trim());
        trip.setDestination(request.destination().trim());
        trip.setDepartureAt(request.departureAt());
        trip.setReturnAt(request.returnAt());
    }

    private void addParticipants(Trip trip, List<ParticipantRequest> requests, Vehicle vehicle) {
        List<ParticipantRequest> list = requests != null ? requests : List.of();
        if (list.size() > vehicle.getCapacity()) {
            throw new BusinessException("O veículo " + vehicle.getPlate() + " leva " + vehicle.getCapacity()
                + " passageiro(s); a viagem tem " + list.size());
        }
        boolean canIncludeClients = list.stream().noneMatch(p -> p.type() == ParticipantType.CLIENT)
            || access.canSeeClientNames();
        if (!canIncludeClients) {
            throw new ForbiddenException("Sem permissão para incluir atendidas como participantes (é preciso poder ler "
                + "o nome das atendidas)");
        }

        Set<UUID> users = new HashSet<>();
        Set<UUID> clients = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            ParticipantRequest request = list.get(i);
            String path = "participants[" + i + "]";
            TripParticipant participant = TripParticipant.builder()
                .trip(trip)
                .participantType(request.type())
                .phone(FleetMapper.normalize(request.phone()))
                .document(FleetMapper.normalize(request.document()))
                .notes(FleetMapper.normalize(request.notes()))
                .build();
            switch (request.type()) {
                case USER -> {
                    if (request.userId() == null) {
                        throw invalid(path + ".userId", "obrigatório para participante USER");
                    }
                    if (!users.add(request.userId())) {
                        throw invalid(path + ".userId", "usuário repetido na viagem");
                    }
                    User user = userRepository.findById(request.userId())
                        .orElseThrow(() -> new NotFoundException("Usuário não encontrado: " + request.userId()));
                    participant.setUser(user);
                    participant.setName(user.getName());
                }
                case CLIENT -> {
                    if (request.clientId() == null) {
                        throw invalid(path + ".clientId", "obrigatório para participante CLIENT");
                    }
                    if (!clients.add(request.clientId())) {
                        throw invalid(path + ".clientId", "atendida repetida na viagem");
                    }
                    Client client = clientRepository.findById(request.clientId())
                        .orElseThrow(() -> new NotFoundException("Atendida não encontrada: " + request.clientId()));
                    participant.setClient(client);
                    participant.setName(client.getNome());
                }
                case EXTERNAL -> {
                    String name = FleetMapper.normalize(request.name());
                    if (name == null) {
                        throw invalid(path + ".name", "obrigatório para participante EXTERNAL");
                    }
                    participant.setName(name);
                }
            }
            trip.getParticipants().add(participant);
        }
    }

    private static FieldValidationException invalid(String field, String message) {
        return new FieldValidationException("Dados inválidos", Map.of(field, message));
    }

    private static Specification<Trip> spec(LocalDate from, LocalDate to, UUID driverId, UUID vehicleId, TripStatus status) {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("driver", JoinType.INNER);
                root.fetch("vehicle", JoinType.INNER);
            }
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThan(root.get("returnAt"), from.atStartOfDay()));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("departureAt"), to.plusDays(1).atStartOfDay()));
            }
            if (driverId != null) {
                predicates.add(cb.equal(root.get("driver").get("id"), driverId));
            }
            if (vehicleId != null) {
                predicates.add(cb.equal(root.get("vehicle").get("id"), vehicleId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    // ---------------------------------------------------------------- respostas

    private List<TripResponse> toResponses(List<Trip> trips) {
        boolean canSeeClients = access.canSeeClientNames();
        return trips.stream().map(trip -> toResponse(trip, canSeeClients)).toList();
    }

    private static TripResponse toResponse(Trip trip, boolean canSeeClients) {
        List<ParticipantResponse> participants = trip.getParticipants().stream()
            .map(participant -> participant(participant, canSeeClients))
            .toList();
        Integer distance = trip.getStartOdometerKm() != null && trip.getEndOdometerKm() != null
            ? trip.getEndOdometerKm() - trip.getStartOdometerKm() : null;
        return new TripResponse(
            trip.getId(),
            trip.getNumber(),
            trip.getStatus(),
            trip.getStatus().getLabel(),
            trip.getPurpose(),
            trip.getDescription(),
            trip.getDepartureLocation(),
            trip.getDestination(),
            trip.getDepartureAt(),
            trip.getReturnAt(),
            FleetMapper.driverSummary(trip.getDriver()),
            FleetMapper.vehicleSummary(trip.getVehicle()),
            participants.size(),
            participants,
            trip.getActualDepartureAt(),
            trip.getActualReturnAt(),
            trip.getStartOdometerKm(),
            trip.getEndOdometerKm(),
            distance,
            trip.getCompletionNotes(),
            trip.getCancellationReason(),
            trip.getCancelledAt(),
            trip.getCancelledBy() != null ? trip.getCancelledBy().getId() : null,
            trip.getCreatedBy() != null ? trip.getCreatedBy().getId() : null,
            trip.getCreatedAt(),
            trip.getUpdatedAt()
        );
    }

    private static ParticipantResponse participant(TripParticipant participant, boolean canSeeClients) {
        boolean hidden = participant.getParticipantType() == ParticipantType.CLIENT && !canSeeClients;
        return new ParticipantResponse(
            participant.getId(),
            participant.getParticipantType(),
            participant.getUser() != null ? participant.getUser().getId() : null,
            participant.getClient() != null ? participant.getClient().getId() : null,
            hidden ? null : participant.getName(),
            hidden ? null : participant.getPhone(),
            hidden ? null : participant.getDocument(),
            hidden ? null : participant.getNotes(),
            hidden
        );
    }
}
