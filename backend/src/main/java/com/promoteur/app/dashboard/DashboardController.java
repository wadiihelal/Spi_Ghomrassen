package com.promoteur.app.dashboard;

import com.promoteur.app.report.ReportFilter;
import com.promoteur.app.report.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Tableau de bord", description = "Agrégats du projet actif.")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ReportService reportService;

    /**
     * @param projectId project identifier, or {@code ALL} to aggregate across projects; absent
     *                  means the active project context
     */
    @Operation(summary = "Synthèse du tableau de bord pour le projet actif")
    @GetMapping("/summary")
    public DashboardSummaryResponse summary(@RequestParam(required = false) String projectId) {
        return reportService.globalSummary(ReportFilter.of(projectId, null, null));
    }
}
