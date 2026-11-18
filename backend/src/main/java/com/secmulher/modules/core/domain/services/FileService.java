package com.secmulher.modules.core.domain.services;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.secmulher.modules.core.domain.access.AccessControlService;
import com.secmulher.modules.core.domain.access.CategoryAccess;
import com.secmulher.modules.core.domain.dtos.FileDtos.FileDownload;
import com.secmulher.modules.core.domain.dtos.FileDtos.FileResponse;
import com.secmulher.modules.core.domain.dtos.FileDtos.FileUpdateRequest;
import com.secmulher.modules.core.domain.enums.RecordType;
import com.secmulher.modules.core.domain.exceptions.BusinessException;
import com.secmulher.modules.core.domain.exceptions.NotFoundException;
import com.secmulher.modules.core.infra.entities.FileRecord;
import com.secmulher.modules.core.infra.entities.enums.ActivityType;
import com.secmulher.modules.core.infra.entities.enums.ConfidentialityLevel;
import com.secmulher.modules.core.infra.repositories.FileRecordRepository;
import com.secmulher.modules.core.infra.security.CurrentUserProvider;
import com.secmulher.modules.core.infra.storage.FileStorage;

// Acesso a documentos = permissão na categoria do registro + nível de sigilo do documento
// dentro do limite da permissão (max_confidentiality)
@Service
public class FileService {
    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    final FileRecordRepository fileRecordRepository;
    final FileStorage fileStorage;
    final RecordService recordService;
    final ActivityService activityService;
    final CurrentUserProvider currentUserProvider;
    final AccessControlService accessControl;

    public FileService(FileRecordRepository fileRecordRepository, FileStorage fileStorage,
            RecordService recordService, ActivityService activityService, CurrentUserProvider currentUserProvider,
            AccessControlService accessControl) {
        this.fileRecordRepository = fileRecordRepository;
        this.fileStorage = fileStorage;
        this.recordService = recordService;
        this.activityService = activityService;
        this.currentUserProvider = currentUserProvider;
        this.accessControl = accessControl;
    }

    private FileRecord getFileById(UUID id) {
        return fileRecordRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Arquivo não encontrado: " + id));
    }

    private CategoryAccess accessFor(FileRecord file) {
        return accessControl.forRecord(RecordType.fromTableName(file.getEntityType()), file.getEntityId());
    }

    // Detalhes gravados no histórico; "sigilo" permite ocultar o evento de quem não tem o nível
    private static Map<String, Object> details(FileRecord file) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("arquivo", file.getOriginalName());
        details.put("sigilo", file.getConfidentialityLevel().name());
        return details;
    }

    @Transactional(readOnly = true)
    public List<FileResponse> findAll(RecordType type, UUID recordId) {
        recordService.ensureExists(type, recordId);
        CategoryAccess access = accessControl.forRecord(type, recordId).requireRead();
        return fileRecordRepository.findByEntityTypeAndEntityIdOrderByUploadedAtDesc(type.getTableName(), recordId)
            .stream()
            .filter(file -> access.canAccess(file.getConfidentialityLevel()))
            .map(FileResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public FileResponse findById(UUID id) {
        FileRecord file = getFileById(id);
        accessFor(file).requireRead().requireConfidentiality(file.getConfidentialityLevel());
        return FileResponse.from(file);
    }

    @Transactional
    public FileResponse upload(RecordType type, UUID recordId, MultipartFile upload, ConfidentialityLevel level) {
        if (upload == null || upload.isEmpty()) {
            throw new BusinessException("Nenhum arquivo enviado");
        }
        recordService.ensureExists(type, recordId);
        accessControl.forRecord(type, recordId).requireUpdate().requireConfidentiality(level);

        // O nome físico é um UUID; o nome original só é guardado como metadado
        String storagePath = type.getTableName() + "/" + recordId + "/" + UUID.randomUUID();
        String originalName = sanitizeFileName(upload.getOriginalFilename());

        try (InputStream content = upload.getInputStream()) {
            fileStorage.store(content, storagePath);
        } catch (IOException ex) {
            throw new UncheckedIOException("Falha ao ler o arquivo enviado", ex);
        }
        runAfterRollback(() -> fileStorage.delete(storagePath));

        FileRecord file = fileRecordRepository.saveAndFlush(FileRecord.builder()
            .entityType(type.getTableName())
            .entityId(recordId)
            .uploadedBy(currentUserProvider.getCurrentUser().orElse(null))
            .originalName(originalName)
            .storagePath(storagePath)
            .fileSizeBytes(upload.getSize())
            .contentType(upload.getContentType())
            .confidentialityLevel(level)
            .build());

        activityService.record(type, recordId, ActivityType.FILE_UPLOADED, "Documento anexado: " + originalName, details(file));
        return FileResponse.from(file);
    }

    // Downloads de documentos restritos/sigilosos ficam registrados na linha do tempo (LGPD)
    @Transactional
    public FileDownload download(UUID id) {
        FileRecord file = getFileById(id);
        accessFor(file).requireRead().requireConfidentiality(file.getConfidentialityLevel());

        if (file.getConfidentialityLevel() != ConfidentialityLevel.INTERNAL) {
            activityService.record(RecordType.fromTableName(file.getEntityType()), file.getEntityId(),
                ActivityType.FILE_DOWNLOADED, "Documento acessado: " + file.getOriginalName(), details(file));
        }

        return new FileDownload(file.getOriginalName(), file.getContentType(), fileStorage.load(file.getStoragePath()));
    }

    @Transactional
    public FileResponse updateConfidentiality(UUID id, FileUpdateRequest request) {
        FileRecord file = getFileById(id);
        ConfidentialityLevel previous = file.getConfidentialityLevel();
        CategoryAccess access = accessFor(file).requireUpdate();
        access.requireConfidentiality(previous);
        access.requireConfidentiality(request.confidentialityLevel());

        if (previous != request.confidentialityLevel()) {
            file.setConfidentialityLevel(request.confidentialityLevel());
            Map<String, Object> details = details(file);
            details.put("sigilo_anterior", previous.name());
            activityService.record(RecordType.fromTableName(file.getEntityType()), file.getEntityId(),
                ActivityType.FILE_UPDATED, "Nível de sigilo alterado: " + file.getOriginalName(), details);
        }

        return FileResponse.from(fileRecordRepository.saveAndFlush(file));
    }

    @Transactional
    public void delete(UUID id) {
        FileRecord file = getFileById(id);
        accessFor(file).requireDelete().requireConfidentiality(file.getConfidentialityLevel());

        activityService.record(RecordType.fromTableName(file.getEntityType()), file.getEntityId(),
            ActivityType.FILE_REMOVED, "Documento removido: " + file.getOriginalName(), details(file));
        fileRecordRepository.delete(file);
        runAfterCommit(() -> fileStorage.delete(file.getStoragePath()));
    }

    // Usado quando o registro dono dos arquivos é excluído (a permissão já foi checada por quem chama)
    @Transactional
    public void deleteAllFor(RecordType type, UUID recordId) {
        List<FileRecord> files = fileRecordRepository
            .findByEntityTypeAndEntityIdOrderByUploadedAtDesc(type.getTableName(), recordId);
        fileRecordRepository.deleteAll(files);
        runAfterCommit(() -> files.forEach(file -> fileStorage.delete(file.getStoragePath())));
    }

    private static String sanitizeFileName(String originalFilename) {
        String name = StringUtils.getFilename(StringUtils.cleanPath(
            originalFilename == null ? "" : originalFilename.replace('\\', '/')));
        if (name == null || name.isBlank()) {
            return "arquivo";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    // O disco não participa da transação: só apaga o arquivo físico depois que o banco confirmou
    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (RuntimeException ex) {
                    log.warn("Falha ao remover arquivo físico após commit", ex);
                }
            }
        });
    }

    // Se o banco falhar depois do upload, remove o arquivo físico que ficaria órfão
    private void runAfterRollback(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        action.run();
                    } catch (RuntimeException ex) {
                        log.warn("Falha ao remover arquivo físico após rollback", ex);
                    }
                }
            }
        });
    }
}
