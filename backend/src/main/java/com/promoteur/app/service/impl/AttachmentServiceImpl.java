package com.promoteur.app.service.impl;

import com.promoteur.app.dto.response.AttachmentResponse;
import com.promoteur.app.entity.FileAttachment;
import com.promoteur.app.enums.AttachmentOwnerType;
import com.promoteur.app.exception.ResourceNotFoundException;
import com.promoteur.app.mapper.AttachmentMapper;
import com.promoteur.app.repository.ClientAdvanceRepository;
import com.promoteur.app.repository.ClientPurchaseRepository;
import com.promoteur.app.repository.ExpenseRepository;
import com.promoteur.app.repository.FileAttachmentRepository;
import com.promoteur.app.repository.SupplierInvoiceRepository;
import com.promoteur.app.service.AttachmentService;
import com.promoteur.app.service.AuditLogService;
import com.promoteur.app.service.MessageService;
import com.promoteur.app.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {

    /** Ten megabytes; the servlet multipart limit in application.properties mirrors it. */
    static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    /** Transfer slips, cheque scans and contract pages come as one of these three. */
    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "application/pdf", "pdf",
            "image/jpeg", "jpg",
            "image/png", "png");

    private static final Set<String> ACCEPTED_CONTENT_TYPES = EXTENSION_BY_CONTENT_TYPE.keySet();

    /** Recorded as the uploader while the application has no login (SEC-01). */
    private static final String SYSTEM_ACTOR = "system";

    private final FileAttachmentRepository fileAttachmentRepository;
    private final ExpenseRepository expenseRepository;
    private final ClientAdvanceRepository clientAdvanceRepository;
    private final ClientPurchaseRepository clientPurchaseRepository;
    private final SupplierInvoiceRepository supplierInvoiceRepository;
    private final StorageService storageService;
    private final AuditLogService auditLogService;
    private final MessageService messageService;
    private final AttachmentMapper attachmentMapper;

    @Override
    public AttachmentResponse upload(final AttachmentOwnerType ownerType, final Long ownerId, final MultipartFile file) {
        this.requireOwner(ownerType, ownerId);
        this.validate(file);

        final String contentType = file.getContentType();
        final String storageKey;
        try (InputStream content = file.getInputStream()) {
            storageKey = this.storageService.store(content, EXTENSION_BY_CONTENT_TYPE.get(contentType));
        } catch (IOException ex) {
            throw new UncheckedIOException(this.messageService.get("attachment.storeFailed"), ex);
        }

        final FileAttachment attachment = new FileAttachment();
        attachment.setOriginalName(this.safeName(file.getOriginalFilename()));
        attachment.setStorageKey(storageKey);
        attachment.setContentType(contentType);
        attachment.setSizeBytes(file.getSize());
        attachment.setUploadedBy(SYSTEM_ACTOR);
        attachment.setUploadedAt(LocalDateTime.now());
        attachment.setOwnerType(ownerType);
        attachment.setOwnerId(ownerId);
        final FileAttachment saved = this.fileAttachmentRepository.save(attachment);

        this.auditLogService.create(ownerType.name(), ownerId, "ATTACH",
                this.messageService.get("audit.attachment.added", saved.getOriginalName()));
        return this.attachmentMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttachmentResponse> findByOwner(final AttachmentOwnerType ownerType, final Long ownerId) {
        return this.fileAttachmentRepository.findByOwnerTypeAndOwnerIdOrderByUploadedAtDesc(ownerType, ownerId)
                .stream()
                .map(this.attachmentMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AttachmentResponse findById(final Long id) {
        return this.attachmentMapper.toResponse(this.entity(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Resource content(final Long id) {
        final Resource resource = this.storageService.load(this.entity(id).getStorageKey());
        if (!resource.exists()) {
            throw new ResourceNotFoundException(this.messageService.get("attachment.contentMissing", String.valueOf(id)));
        }
        return resource;
    }

    @Override
    public void delete(final Long id) {
        final FileAttachment attachment = this.entity(id);
        final String originalName = attachment.getOriginalName();
        final String storageKey = attachment.getStorageKey();
        final AttachmentOwnerType ownerType = attachment.getOwnerType();
        final Long ownerId = attachment.getOwnerId();

        this.fileAttachmentRepository.delete(attachment);
        try {
            this.storageService.delete(storageKey);
        } catch (IOException ex) {
            throw new UncheckedIOException(this.messageService.get("attachment.deleteFailed"), ex);
        }
        this.auditLogService.create(ownerType.name(), ownerId, "DETACH",
                this.messageService.get("audit.attachment.removed", originalName));
    }

    private FileAttachment entity(final Long id) {
        return this.fileAttachmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        this.messageService.get("attachment.notFound", String.valueOf(id))));
    }

    /** A file may only be attached to a document that exists. */
    private void requireOwner(final AttachmentOwnerType ownerType, final Long ownerId) {
        final boolean exists = switch (ownerType) {
            case EXPENSE -> this.expenseRepository.existsById(ownerId);
            case CLIENT_ADVANCE -> this.clientAdvanceRepository.existsById(ownerId);
            case CLIENT_PURCHASE -> this.clientPurchaseRepository.existsById(ownerId);
            case SUPPLIER_INVOICE -> this.supplierInvoiceRepository.existsById(ownerId);
        };
        if (!exists) {
            throw new ResourceNotFoundException(
                    this.messageService.get("attachment.ownerNotFound", ownerType.name(), String.valueOf(ownerId)));
        }
    }

    private void validate(final MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(this.messageService.get("attachment.empty"));
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException(this.messageService.get("attachment.tooLarge"));
        }
        if (file.getContentType() == null || !ACCEPTED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException(
                    this.messageService.get("attachment.unsupportedType", String.valueOf(file.getContentType())));
        }
    }

    /** Keeps only the file name part of whatever the browser sent, and never an empty one. */
    private String safeName(final String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "piece-jointe";
        }
        final String name = originalFilename.replace('\\', '/');
        final String last = name.substring(name.lastIndexOf('/') + 1).trim();
        return last.isEmpty() ? "piece-jointe" : last;
    }
}
