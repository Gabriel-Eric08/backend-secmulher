package com.secmulher.modules.core.infra.storage;

import java.io.InputStream;

import org.springframework.core.io.Resource;

// Abstração do local físico dos arquivos (disco local hoje; S3/GCP no futuro)
public interface FileStorage {
    void store(InputStream content, String storagePath);

    Resource load(String storagePath);

    void delete(String storagePath);
}
