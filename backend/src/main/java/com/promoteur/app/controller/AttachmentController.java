package com.promoteur.app.controller;

import com.promoteur.app.dto.response.AttachmentResponse;
import com.promoteur.app.enums.AttachmentOwnerType;
import com.promoteur.app.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Proof files attached to business documents (FE-05). PDF, JPEG or PNG, at most 10 MB.
 *
 * <p>The plan restricted DELETE to ADMIN; the application has no roles by decision, so it is
 * open like every other endpoint (SEC-01).</p>
 */
@Tag(name = "Pièces jointes", description = "Justificatifs (PDF, JPEG, PNG) rattachés aux documents.")
@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @Operation(summary = "Téléversement d’un justificatif (multipart)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentResponse upload(
            @RequestParam AttachmentOwnerType ownerType,
            @RequestParam Long ownerId,
            @RequestPart("file") MultipartFile file) {
        return attachmentService.upload(ownerType, ownerId, file);
    }

    @Operation(summary = "Liste des justificatifs d’un document")
    @GetMapping
    public List<AttachmentResponse> findByOwner(
            @RequestParam AttachmentOwnerType ownerType,
            @RequestParam Long ownerId) {
        return attachmentService.findByOwner(ownerType, ownerId);
    }

    /** Streams the file inline under its original name. */
    @Operation(summary = "Téléchargement du fichier")
    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        AttachmentResponse metadata = attachmentService.findById(id);
        Resource content = attachmentService.content(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(metadata.contentType()));
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(metadata.originalName(), StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(metadata.sizeBytes());
        return ResponseEntity.ok().headers(headers).body(content);
    }

    @Operation(summary = "Suppression d’un justificatif")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        attachmentService.delete(id);
    }
}
