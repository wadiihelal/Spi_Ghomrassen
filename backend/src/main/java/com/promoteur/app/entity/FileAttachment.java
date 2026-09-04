package com.promoteur.app.entity;

import com.promoteur.app.enums.AttachmentOwnerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A stored proof file — transfer slip, cheque scan, signed contract page — linked to one
 * business document by ({@link #ownerType}, {@link #ownerId}) (FE-05).
 *
 * <p>The bytes live behind {@code StorageService} under {@link #storageKey}; this row is the
 * record of what was stored, by whom, and for which document.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "file_attachments")
public class FileAttachment extends BaseEntity {

    /** File name as uploaded, shown to the user and used when the file is served. */
    @Column(nullable = false)
    private String originalName;

    /** Opaque key the storage backend uses to find the bytes; never derived from user input. */
    @Column(nullable = false, unique = true)
    private String storageKey;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private Long sizeBytes;

    /** {@code system} while the application has no login (SEC-01). */
    @Column(nullable = false)
    private String uploadedBy;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AttachmentOwnerType ownerType;

    @Column(nullable = false)
    private Long ownerId;
}
