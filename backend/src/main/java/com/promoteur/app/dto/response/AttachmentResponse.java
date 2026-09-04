package com.promoteur.app.dto.response;

import com.promoteur.app.enums.AttachmentOwnerType;

import java.time.LocalDateTime;

/**
 * A stored proof file as the API returns it. The bytes are served by
 * {@code GET /api/attachments/{id}}; {@code storageKey} is internal and never exposed.
 */
public record AttachmentResponse(
        Long id,
        String originalName,
        String contentType,
        Long sizeBytes,
        String uploadedBy,
        LocalDateTime uploadedAt,
        AttachmentOwnerType ownerType,
        Long ownerId
) {
}
