package com.secmulher.modules.core.domain.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.core.io.Resource;

import com.secmulher.modules.core.infra.entities.FileRecord;
import com.secmulher.modules.core.infra.entities.enums.ConfidentialityLevel;

import jakarta.validation.constraints.NotNull;

public final class FileDtos {
    private FileDtos() {}

    public record FileUpdateRequest(
        @NotNull ConfidentialityLevel confidentialityLevel
    ) {}

    // storagePath fica de fora de propósito: é detalhe interno do armazenamento
    public record FileResponse(
        UUID id,
        String entityType,
        UUID entityId,
        UUID uploadedBy,
        String originalName,
        String contentType,
        Long fileSizeBytes,
        ConfidentialityLevel confidentialityLevel,
        LocalDateTime uploadedAt
    ) {
        public static FileResponse from(FileRecord file) {
            return new FileResponse(
                file.getId(),
                file.getEntityType(),
                file.getEntityId(),
                file.getUploadedBy() != null ? file.getUploadedBy().getId() : null,
                file.getOriginalName(),
                file.getContentType(),
                file.getFileSizeBytes(),
                file.getConfidentialityLevel(),
                file.getUploadedAt()
            );
        }
    }

    public record FileDownload(
        String originalName,
        String contentType,
        Resource content
    ) {}
}
