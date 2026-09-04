package com.promoteur.app.service;

import com.promoteur.app.dto.response.AttachmentResponse;
import com.promoteur.app.enums.AttachmentOwnerType;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Proof files attached to business documents (FE-05).
 */
public interface AttachmentService {

    /**
     * Stores a file against a document. Accepts PDF, JPEG and PNG up to 10 MB; anything else is
     * refused with a French message.
     *
     * @throws com.promoteur.app.exception.ResourceNotFoundException when the owner does not exist
     */
    AttachmentResponse upload(AttachmentOwnerType ownerType, Long ownerId, MultipartFile file);

    /**
     * Lists the files attached to a document, most recent first.
     */
    List<AttachmentResponse> findByOwner(AttachmentOwnerType ownerType, Long ownerId);

    /**
     * Metadata of one attachment.
     */
    AttachmentResponse findById(Long id);

    /**
     * The stored bytes, for streaming.
     */
    Resource content(Long id);

    /**
     * Removes the row and the stored bytes.
     */
    void delete(Long id);
}
