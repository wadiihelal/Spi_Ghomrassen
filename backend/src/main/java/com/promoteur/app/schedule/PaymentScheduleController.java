package com.promoteur.app.schedule;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The payment schedule of one sale contract (UX-03).
 */
@Tag(name = "Échéancier", description = "Plan de paiement d’un contrat de vente : réservation, tranches, livraison.")
@RestController
@RequestMapping("/api/client-purchases/{purchaseId}/schedule")
@RequiredArgsConstructor
public class PaymentScheduleController {

    private final PaymentScheduleService paymentScheduleService;

    @Operation(summary = "Échéancier du contrat, avec le règlement de chaque échéance")
    @GetMapping
    public PaymentScheduleResponse findByPurchase(@PathVariable Long purchaseId) {
        return paymentScheduleService.findByPurchase(purchaseId);
    }

    @Operation(summary = "Remplacement complet de l’échéancier")
    @PutMapping
    public PaymentScheduleResponse replace(@PathVariable Long purchaseId,
                                           @Valid @RequestBody PaymentScheduleRequest request) {
        return paymentScheduleService.replace(purchaseId, request);
    }

    @Operation(summary = "Génération de l’échéancier depuis un modèle en pourcentages")
    @PostMapping("/generate")
    public PaymentScheduleResponse generate(@PathVariable Long purchaseId,
                                            @Valid @RequestBody ScheduleTemplateRequest request) {
        return paymentScheduleService.generate(purchaseId, request);
    }

    @Operation(summary = "Suppression de l’échéancier, sans toucher aux encaissements")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@PathVariable Long purchaseId) {
        paymentScheduleService.clear(purchaseId);
    }
}
