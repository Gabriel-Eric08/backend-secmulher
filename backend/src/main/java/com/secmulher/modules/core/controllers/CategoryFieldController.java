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

import com.secmulher.modules.core.domain.dtos.CategoryFieldDtos.CategoryFieldRequest;
import com.secmulher.modules.core.domain.dtos.CategoryFieldDtos.CategoryFieldResponse;
import com.secmulher.modules.core.domain.services.CategoryFieldService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/categories/{categoryId}/fields")
public class CategoryFieldController {
    final CategoryFieldService categoryFieldService;

    public CategoryFieldController(CategoryFieldService categoryFieldService) {
        this.categoryFieldService = categoryFieldService;
    }

    @GetMapping
    public List<CategoryFieldResponse> findAll(@PathVariable UUID categoryId) {
        return categoryFieldService.findAll(categoryId);
    }

    @PostMapping
    public ResponseEntity<CategoryFieldResponse> create(@PathVariable UUID categoryId,
            @RequestBody @Valid CategoryFieldRequest request) {
        CategoryFieldResponse created = categoryFieldService.create(categoryId, request);
        return ResponseEntity.created(URI.create("/categories/" + categoryId + "/fields/" + created.id())).body(created);
    }

    @PutMapping("/{fieldId}")
    public CategoryFieldResponse update(@PathVariable UUID categoryId, @PathVariable UUID fieldId,
            @RequestBody @Valid CategoryFieldRequest request) {
        return categoryFieldService.update(categoryId, fieldId, request);
    }

    @DeleteMapping("/{fieldId}")
    public ResponseEntity<Void> delete(@PathVariable UUID categoryId, @PathVariable UUID fieldId,
            @RequestParam(defaultValue = "false") boolean purgeData) {
        categoryFieldService.delete(categoryId, fieldId, purgeData);
        return ResponseEntity.noContent().build();
    }
}
