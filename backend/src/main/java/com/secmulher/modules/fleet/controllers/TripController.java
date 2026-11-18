package com.secmulher.modules.fleet.controllers;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.secmulher.modules.fleet.domain.dtos.TripDtos.AvailabilityResponse;
import com.secmulher.modules.fleet.domain.dtos.TripDtos.CancelTripRequest;
import com.secmulher.modules.fleet.domain.dtos.TripDtos.CompleteTripRequest;
import com.secmulher.modules.fleet.domain.dtos.TripDtos.StartTripRequest;
import com.secmulher.modules.fleet.domain.dtos.TripDtos.TripRequest;
import com.secmulher.modules.fleet.domain.dtos.TripDtos.TripResponse;
import com.secmulher.modules.fleet.domain.services.TripService;
import com.secmulher.modules.fleet.infra.entities.enums.TripStatus;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/fleet")
public class TripController {
    final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    // Agenda: viagens que se sobrepõem ao período [from, to], em ordem de saída
    @GetMapping("/trips")
    public List<TripResponse> search(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID driverId,
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) TripStatus status) {
        return tripService.search(from, to, driverId, vehicleId, status);
    }

    // Agenda do motorista vinculado ao usuário logado
    @GetMapping("/trips/mine")
    public List<TripResponse> mine(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return tripService.mine(from, to);
    }

    @GetMapping("/trips/{id}")
    public TripResponse findById(@PathVariable UUID id) {
        return tripService.findById(id);
    }

    @PostMapping("/trips")
    public ResponseEntity<TripResponse> create(@RequestBody @Valid TripRequest request) {
        TripResponse created = tripService.create(request);
        return ResponseEntity.created(URI.create("/fleet/trips/" + created.id())).body(created);
    }

    @PutMapping("/trips/{id}")
    public TripResponse update(@PathVariable UUID id, @RequestBody @Valid TripRequest request) {
        return tripService.update(id, request);
    }

    @PostMapping("/trips/{id}/start")
    public TripResponse start(@PathVariable UUID id, @RequestBody @Valid StartTripRequest request) {
        return tripService.start(id, request);
    }

    @PostMapping("/trips/{id}/complete")
    public TripResponse complete(@PathVariable UUID id, @RequestBody @Valid CompleteTripRequest request) {
        return tripService.complete(id, request);
    }

    @PostMapping("/trips/{id}/cancel")
    public TripResponse cancel(@PathVariable UUID id, @RequestBody @Valid CancelTripRequest request) {
        return tripService.cancel(id, request);
    }

    // Motoristas e veículos livres no intervalo (ex.: ?start=2026-11-20T08:00&end=2026-11-20T17:00)
    @GetMapping("/availability")
    public AvailabilityResponse availability(
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime end) {
        return tripService.availability(start, end);
    }
}
