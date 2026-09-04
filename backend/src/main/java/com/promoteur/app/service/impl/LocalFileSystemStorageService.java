package com.promoteur.app.service.impl;

import com.promoteur.app.service.StorageService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Attachments on the local filesystem, under a root read from {@code app.storage.root}.
 *
 * <p>The root must sit outside the application directory: a redeploy or a container rebuild
 * must never take the proofs with it. Keys are {@code yyyy/MM/<uuid>.<ext>}, generated here,
 * so no user-supplied name ever becomes part of a path.</p>
 */
@Service
public class LocalFileSystemStorageService implements StorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalFileSystemStorageService.class);

    private final Path root;

    public LocalFileSystemStorageService(@Value("${app.storage.root}") final String root) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
    }

    @PostConstruct
    void ensureRootExists() throws IOException {
        Files.createDirectories(this.root);
        LOGGER.info("Pièces jointes stockées sous {}", this.root);
    }

    @Override
    public String store(final InputStream content, final String extension) throws IOException {
        final LocalDate today = LocalDate.now();
        final String key = String.format("%d/%02d/%s.%s", today.getYear(), today.getMonthValue(),
                UUID.randomUUID(), extension);
        final Path target = this.resolve(key);
        Files.createDirectories(target.getParent());
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        return key;
    }

    @Override
    public Resource load(final String storageKey) {
        return new PathResource(this.resolve(storageKey));
    }

    @Override
    public void delete(final String storageKey) throws IOException {
        Files.deleteIfExists(this.resolve(storageKey));
    }

    /** Resolves a key under the root and refuses anything that would escape it. */
    private Path resolve(final String storageKey) {
        final Path path = this.root.resolve(storageKey).normalize();
        if (!path.startsWith(this.root)) {
            throw new IllegalArgumentException("Clé de stockage invalide : " + storageKey);
        }
        return path;
    }
}
