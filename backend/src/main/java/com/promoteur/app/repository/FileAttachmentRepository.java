package com.promoteur.app.repository;

import com.promoteur.app.entity.FileAttachment;
import com.promoteur.app.enums.AttachmentOwnerType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileAttachmentRepository extends JpaRepository<FileAttachment, Long> {

    List<FileAttachment> findByOwnerTypeAndOwnerIdOrderByUploadedAtDesc(AttachmentOwnerType ownerType, Long ownerId);
}
