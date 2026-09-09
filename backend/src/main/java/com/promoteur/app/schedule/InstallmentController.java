package com.promoteur.app.schedule;

import com.promoteur.app.shared.ListFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Instalments across every contract: what is late, and what falls due (UX-03).
 */
@Tag(name = "Échéances", description = "Vue transverse des échéances : retards et échéances du mois.")
@RestController
@RequestMapping("/api/installments")
@RequiredArgsConstructor
public class InstallmentController {

    private final PaymentScheduleService paymentScheduleService;

    /**
     * @param status   keep only PAID, PARTIALLY_PAID, OVERDUE or UPCOMING
     * @param dueFrom, dueTo bounds on the due date, inclusive
     */
    @Operation(summary = "Recherche d’échéances par projet, client, statut et période")
    @GetMapping
    public List<PaymentInstallmentResponse> search(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) InstallmentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo) {
        return paymentScheduleService.search(
                new ListFilter(projectId, clientId, null, null, null, null, null, dueFrom, dueTo, null),
                status);
    }

    @Operation(summary = "Retards et échéances du mois pour le projet actif")
    @GetMapping("/summary")
    public InstallmentSummaryResponse summary(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reference) {
        return paymentScheduleService.summary(projectId, reference);
    }
}
