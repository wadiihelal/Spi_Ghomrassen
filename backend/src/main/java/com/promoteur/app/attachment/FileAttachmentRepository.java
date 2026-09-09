package com.promoteur.app.attachment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileAttachmentRepository extends JpaRepository<FileAttachment, Long> {

    List<FileAttachment> findByOwnerTypeAndOwnerIdOrderByUploadedAtDesc(AttachmentOwnerType ownerType, Long ownerId);
}
