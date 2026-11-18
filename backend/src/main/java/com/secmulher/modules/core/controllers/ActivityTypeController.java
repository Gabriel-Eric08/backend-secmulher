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

import com.secmulher.modules.core.domain.dtos.ActivityTypeDtos.ActivityTypeRequest;
import com.secmulher.modules.core.domain.dtos.ActivityTypeDtos.ActivityTypeResponse;
import com.secmulher.modules.core.domain.services.ActivityTypeService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/categories/{categoryId}/activity-types")
public class ActivityTypeController {
    final ActivityTypeService activityTypeService;

    public ActivityTypeController(ActivityTypeService activityTypeService) {
        this.activityTypeService = activityTypeService;
    }

    @GetMapping
    public List<ActivityTypeResponse> findAll(@PathVariable UUID categoryId,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return activityTypeService.findAll(categoryId, includeInactive);
    }

    @PostMapping
    public ResponseEntity<ActivityTypeResponse> create(@PathVariable UUID categoryId,
            @RequestBody @Valid ActivityTypeRequest request) {
        ActivityTypeResponse created = activityTypeService.create(categoryId, request);
        return ResponseEntity.created(URI.create("/categories/" + categoryId + "/activity-types/" + created.id()))
            .body(created);
    }

    @PutMapping("/{typeId}")
    public ActivityTypeResponse update(@PathVariable UUID categoryId, @PathVariable UUID typeId,
            @RequestBody @Valid ActivityTypeRequest request) {
        return activityTypeService.update(categoryId, typeId, request);
    }

    @DeleteMapping("/{typeId}")
    public ResponseEntity<Void> delete(@PathVariable UUID categoryId, @PathVariable UUID typeId) {
        activityTypeService.delete(categoryId, typeId);
        return ResponseEntity.noContent().build();
    }
}
