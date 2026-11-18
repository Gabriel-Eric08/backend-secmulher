package com.secmulher.modules.core.controllers;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.secmulher.modules.core.domain.dtos.ActivityDtos.ActivityResponse;
import com.secmulher.modules.core.domain.dtos.ActivityDtos.ManualActivityRequest;
import com.secmulher.modules.core.domain.dtos.FileDtos.FileResponse;
import com.secmulher.modules.core.domain.dtos.RecordOverviewDto;
import com.secmulher.modules.core.domain.enums.RecordType;
import com.secmulher.modules.core.domain.services.ActivityService;
import com.secmulher.modules.core.domain.services.FileService;
import com.secmulher.modules.core.domain.services.RecordOverviewService;
import com.secmulher.modules.core.infra.entities.enums.ConfidentialityLevel;

import jakarta.validation.Valid;

// Rotas compartilhadas por qualquer tipo de registro: linha do tempo, documentos e visão 360°
@RestController
@RequestMapping("/{recordType:clients|dynamic-entities}/{recordId}")
public class RecordController {
    final ActivityService activityService;
    final FileService fileService;
    final RecordOverviewService recordOverviewService;

    public RecordController(ActivityService activityService, FileService fileService,
            RecordOverviewService recordOverviewService) {
        this.activityService = activityService;
        this.fileService = fileService;
        this.recordOverviewService = recordOverviewService;
    }

    @GetMapping("/overview")
    public RecordOverviewDto overview(@PathVariable String recordType, @PathVariable UUID recordId) {
        return recordOverviewService.getOverview(RecordType.fromPath(recordType), recordId);
    }

    @GetMapping("/activities")
    public List<ActivityResponse> timeline(@PathVariable String recordType, @PathVariable UUID recordId) {
        return activityService.getTimeline(RecordType.fromPath(recordType), recordId);
    }

    @PostMapping("/activities")
    public ResponseEntity<ActivityResponse> createActivity(@PathVariable String recordType, @PathVariable UUID recordId,
            @RequestBody @Valid ManualActivityRequest request) {
        ActivityResponse created = activityService.createManual(RecordType.fromPath(recordType), recordId, request);
        return ResponseEntity.status(201).body(created);
    }

    @GetMapping("/files")
    public List<FileResponse> files(@PathVariable String recordType, @PathVariable UUID recordId) {
        return fileService.findAll(RecordType.fromPath(recordType), recordId);
    }

    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileResponse> upload(@PathVariable String recordType, @PathVariable UUID recordId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "RESTRICTED") ConfidentialityLevel confidentialityLevel) {
        FileResponse created = fileService.upload(RecordType.fromPath(recordType), recordId, file, confidentialityLevel);
        return ResponseEntity.created(URI.create("/files/" + created.id())).body(created);
    }
}
