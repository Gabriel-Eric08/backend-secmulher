package com.secmulher.modules.fleet.infra.entities;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;

import com.secmulher.modules.core.infra.entities.User;
import com.secmulher.modules.fleet.infra.entities.enums.TripStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Viagem: motorista + veículo em um intervalo [departureAt, returnAt), com participantes
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "fleet_trips")
@Entity
public class Trip {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    // Número sequencial gerado pelo banco
    @Generated
    @Column(name = "number", insertable = false, updatable = false)
    private Long number;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TripStatus status;

    @Column(name = "purpose", nullable = false)
    private String purpose;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "departure_location", nullable = false)
    private String departureLocation;

    @Column(name = "destination", nullable = false)
    private String destination;

    // Ida e volta previstas
    @Column(name = "departure_at", nullable = false)
    private LocalDateTime departureAt;

    @Column(name = "return_at", nullable = false)
    private LocalDateTime returnAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    // Execução real
    @Column(name = "actual_departure_at")
    private LocalDateTime actualDepartureAt;

    @Column(name = "actual_return_at")
    private LocalDateTime actualReturnAt;

    @Column(name = "start_odometer_km")
    private Integer startOdometerKm;

    @Column(name = "end_odometer_km")
    private Integer endOdometerKm;

    @Column(name = "completion_notes", columnDefinition = "text")
    private String completionNotes;

    // Cancelamento
    @Column(name = "cancellation_reason", columnDefinition = "text")
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private User cancelledBy;

    // Carregados em lote nas listagens da agenda (evita uma consulta por viagem)
    @Builder.Default
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name")
    private List<TripParticipant> participants = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
