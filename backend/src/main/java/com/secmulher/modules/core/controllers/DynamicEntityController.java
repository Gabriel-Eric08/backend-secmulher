package com.secmulher.modules.core.controllers;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.secmulher.modules.core.domain.dtos.DynamicEntityDtos.DynamicEntityRequest;
import com.secmulher.modules.core.domain.dtos.DynamicEntityDtos.DynamicEntityResponse;
import com.secmulher.modules.core.domain.dtos.DynamicEntityDtos.DynamicEntitySearchRequest;
import com.secmulher.modules.core.domain.services.DynamicEntityService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/dynamic-entities")
public class DynamicEntityController {
    final DynamicEntityService dynamicEntityService;

    public DynamicEntityController(DynamicEntityService dynamicEntityService) {
        this.dynamicEntityService = dynamicEntityService;
    }

    @GetMapping
    public List<DynamicEntityResponse> findAll(@RequestParam(required = false) UUID categoryId) {
        return dynamicEntityService.findAll(categoryId);
    }

    @GetMapping("/{id}")
    public DynamicEntityResponse findById(@PathVariable UUID id) {
        return dynamicEntityService.findById(id);
    }

    // POST porque o filtro é um objeto JSON arbitrário (campos customizados da categoria)
    @PostMapping("/search")
    public List<DynamicEntityResponse> search(@RequestBody @Valid DynamicEntitySearchRequest request) {
        return dynamicEntityService.search(request);
    }

    @PostMapping
    public ResponseEntity<DynamicEntityResponse> create(@RequestBody @Valid DynamicEntityRequest request) {
        DynamicEntityResponse created = dynamicEntityService.create(request);
        return ResponseEntity.created(URI.create("/dynamic-entities/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public DynamicEntityResponse update(@PathVariable UUID id, @RequestBody @Valid DynamicEntityRequest request) {
        return dynamicEntityService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        dynamicEntityService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
