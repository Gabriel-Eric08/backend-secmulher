package com.secmulher.modules.amendments.controllers;

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

import com.secmulher.modules.amendments.domain.dtos.AmendmentSectorDtos.MemberResponse;
import com.secmulher.modules.amendments.domain.dtos.AmendmentSectorDtos.SectorRequest;
import com.secmulher.modules.amendments.domain.dtos.AmendmentSectorDtos.SectorResponse;
import com.secmulher.modules.amendments.domain.services.AmendmentSectorService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/amendments/sectors")
public class AmendmentSectorController {
    final AmendmentSectorService sectorService;

    public AmendmentSectorController(AmendmentSectorService sectorService) {
        this.sectorService = sectorService;
    }

    @GetMapping
    public List<SectorResponse> findAll() {
        return sectorService.findAll();
    }

    @GetMapping("/{id}")
    public SectorResponse findById(@PathVariable UUID id) {
        return sectorService.findById(id);
    }

    @PostMapping
    public ResponseEntity<SectorResponse> create(@RequestBody @Valid SectorRequest request) {
        SectorResponse created = sectorService.create(request);
        return ResponseEntity.created(URI.create("/amendments/sectors/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public SectorResponse update(@PathVariable UUID id, @RequestBody @Valid SectorRequest request) {
        return sectorService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        sectorService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- técnicas do setor

    @GetMapping("/{id}/members")
    public List<MemberResponse> members(@PathVariable UUID id) {
        return sectorService.members(id);
    }

    // Vincula a técnica ao setor (se já estava em outro, passa para este)
    @PutMapping("/{id}/members/{userId}")
    public MemberResponse addMember(@PathVariable UUID id, @PathVariable UUID userId) {
        return sectorService.addMember(id, userId);
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable UUID id, @PathVariable UUID userId) {
        sectorService.removeMember(id, userId);
        return ResponseEntity.noContent().build();
    }
}
