package com.secmulher.modules.core.infra.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.secmulher.modules.core.infra.entities.FileRecord;

public interface FileRecordRepository extends JpaRepository<FileRecord, UUID> {
    List<FileRecord> findByEntityTypeAndEntityIdOrderByUploadedAtDesc(String entityType, UUID entityId);
}
