package com.secmulher.modules.core.controllers;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.secmulher.modules.core.domain.dtos.FileDtos.FileDownload;
import com.secmulher.modules.core.domain.dtos.FileDtos.FileResponse;
import com.secmulher.modules.core.domain.dtos.FileDtos.FileUpdateRequest;
import com.secmulher.modules.core.domain.services.FileService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/files")
public class FileController {
    final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping("/{id}")
    public FileResponse findById(@PathVariable UUID id) {
        return fileService.findById(id);
    }

    // Sempre como anexo, para o navegador não renderizar conteúdo enviado por terceiros
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        FileDownload download = fileService.download(id);
        MediaType mediaType = download.contentType() != null
            ? MediaType.parseMediaType(download.contentType())
            : MediaType.APPLICATION_OCTET_STREAM;

        return ResponseEntity.ok()
            .contentType(mediaType)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(download.originalName(), StandardCharsets.UTF_8)
                .build()
                .toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(download.content());
    }

    @PatchMapping("/{id}")
    public FileResponse updateConfidentiality(@PathVariable UUID id, @RequestBody @Valid FileUpdateRequest request) {
        return fileService.updateConfidentiality(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        fileService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
