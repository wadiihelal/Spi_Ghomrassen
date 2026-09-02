package com.promoteur.app.controller;

import com.promoteur.app.dto.report.ReportFilter;
import com.promoteur.app.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ReportService reportService;

    /**
     * @param projectId project identifier, or {@code ALL} to aggregate across projects; absent
     *                  means the active project context
     */
    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam(required = false) String projectId) {
        return reportService.globalSummary(ReportFilter.of(projectId, null, null));
    }
}
