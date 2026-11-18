package com.secmulher.modules.core.infra.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.secmulher.modules.core.domain.exceptions.NotFoundException;

@Component
public class LocalFileStorage implements FileStorage {
    private final Path basePath;

    public LocalFileStorage(@Value("${storage.local.base-path:./storage}") String basePath) {
        this.basePath = Path.of(basePath).toAbsolutePath().normalize();
    }

    @Override
    public void store(InputStream content, String storagePath) {
        Path target = resolve(storagePath);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new UncheckedIOException("Falha ao salvar arquivo: " + storagePath, ex);
        }
    }

    @Override
    public Resource load(String storagePath) {
        Path target = resolve(storagePath);
        if (!Files.isRegularFile(target)) {
            throw new NotFoundException("Arquivo físico não encontrado: " + storagePath);
        }
        return new FileSystemResource(target);
    }

    @Override
    public void delete(String storagePath) {
        try {
            Files.deleteIfExists(resolve(storagePath));
        } catch (IOException ex) {
            throw new UncheckedIOException("Falha ao remover arquivo: " + storagePath, ex);
        }
    }

    // Impede que um caminho manipulado escape da pasta base (path traversal)
    private Path resolve(String storagePath) {
        Path target = basePath.resolve(storagePath).normalize();
        if (!target.startsWith(basePath)) {
            throw new IllegalArgumentException("Caminho de armazenamento inválido: " + storagePath);
        }
        return target;
    }
}
