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

import com.secmulher.modules.fleet.domain.dtos.DriverDtos.DriverRequest;
import com.secmulher.modules.fleet.domain.dtos.DriverDtos.DriverResponse;
import com.secmulher.modules.fleet.domain.services.DriverService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/fleet/drivers")
public class DriverController {
    final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @GetMapping
    public List<DriverResponse> findAll() {
        return driverService.findAll();
    }

    @GetMapping("/{id}")
    public DriverResponse findById(@PathVariable UUID id) {
        return driverService.findById(id);
    }

    @PostMapping
    public ResponseEntity<DriverResponse> create(@RequestBody @Valid DriverRequest request) {
        DriverResponse created = driverService.create(request);
        return ResponseEntity.created(URI.create("/fleet/drivers/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public DriverResponse update(@PathVariable UUID id, @RequestBody @Valid DriverRequest request) {
        return driverService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        driverService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
