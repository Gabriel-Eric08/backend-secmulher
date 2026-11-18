package com.secmulher.modules.fleet.controllers;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.secmulher.modules.fleet.domain.dtos.VehicleDtos.VehicleRequest;
import com.secmulher.modules.fleet.domain.dtos.VehicleDtos.VehicleResponse;
import com.secmulher.modules.fleet.domain.services.VehicleService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/fleet/vehicles")
public class VehicleController {
    final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public List<VehicleResponse> findAll() {
        return vehicleService.findAll();
    }

    @GetMapping("/{id}")
    public VehicleResponse findById(@PathVariable UUID id) {
        return vehicleService.findById(id);
    }

    @PostMapping
    public ResponseEntity<VehicleResponse> create(@RequestBody @Valid VehicleRequest request) {
        VehicleResponse created = vehicleService.create(request);
        return ResponseEntity.created(URI.create("/fleet/vehicles/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public VehicleResponse update(@PathVariable UUID id, @RequestBody @Valid VehicleRequest request) {
        return vehicleService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        vehicleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
