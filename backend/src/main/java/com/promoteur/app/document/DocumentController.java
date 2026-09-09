package com.promoteur.app.document;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The documents the promoter prints (UX-06): a receipt, a buyer's statement, the month's VAT
 * recap. Every response is a PDF stream; nothing here writes to the database.
 */
@Tag(name = "Documents", description = "Reçus, situations de compte et récapitulatif de TVA, en PDF imprimable.")
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /**
     * Inline rather than attachment: the promoter reads the document on screen and prints it,
     * and the browser's viewer already offers to save it.
     */
    private static ResponseEntity<byte[]> pdf(final byte[] body, final String filename) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(body);
    }

    @Operation(summary = "Reçu de paiement d'un acompte client")
    @GetMapping("/advances/{advanceId}/receipt")
    public ResponseEntity<byte[]> paymentReceipt(@PathVariable Long advanceId) {
        return DocumentController.pdf(documentService.paymentReceipt(advanceId),
                "recu-" + advanceId + ".pdf");
    }

    @Operation(summary = "Situation de compte d'un client")
    @GetMapping("/clients/{clientId}/statement")
    public ResponseEntity<byte[]> clientStatement(@PathVariable Long clientId) {
        return DocumentController.pdf(documentService.clientStatement(clientId),
                "situation-client-" + clientId + ".pdf");
    }

    @Operation(summary = "Récapitulatif de TVA déductible du mois")
    @GetMapping("/vat/{year}/{month}")
    public ResponseEntity<byte[]> vatSummary(@PathVariable int year, @PathVariable int month,
                                             @RequestParam(required = false) Long projectId) {
        return DocumentController.pdf(documentService.vatSummary(year, month, projectId),
                "tva-" + year + "-" + String.format("%02d", month) + ".pdf");
    }
}
