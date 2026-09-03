package com.promoteur.app.controller;

import com.promoteur.app.dto.report.AmountByLabelDto;
import com.promoteur.app.dto.report.ClientStatementDto;
import com.promoteur.app.dto.report.ReportFilter;
import com.promoteur.app.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/expenses/by-category")
    public Page<AmountByLabelDto> expensesByCategory(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Pageable pageable) {
        return reportService.expensesByCategory(ReportFilter.of(projectId, year, month), pageable);
    }

    @GetMapping("/expenses/by-project")
    public Page<AmountByLabelDto> expensesByProject(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Pageable pageable) {
        return reportService.expensesByProject(ReportFilter.of(projectId, year, month), pageable);
    }

    @GetMapping("/expenses/by-month")
    public Page<AmountByLabelDto> expensesByMonth(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Pageable pageable) {
        return reportService.expensesByMonth(ReportFilter.of(projectId, year, month), pageable);
    }

    @GetMapping("/purchases/by-project")
    public Page<AmountByLabelDto> purchasesByProject(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Pageable pageable) {
        return reportService.purchasesByProject(ReportFilter.of(projectId, year, month), pageable);
    }

    @GetMapping("/advances/by-payment-method")
    public Page<AmountByLabelDto> advancesByPaymentMethod(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Pageable pageable) {
        return reportService.advancesByPaymentMethod(ReportFilter.of(projectId, year, month), pageable);
    }

    @GetMapping("/clients/statements")
    public Page<ClientStatementDto> clientStatements(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Pageable pageable) {
        return reportService.clientStatements(ReportFilter.of(projectId, year, month), pageable);
    }

    @GetMapping("/clients/{clientId}/statement")
    public ClientStatementDto clientStatement(
            @PathVariable Long clientId,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return reportService.clientStatement(clientId, ReportFilter.of(projectId, year, month));
    }

    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam("year") Integer year,
            @RequestParam("month") Integer month,
            @RequestParam(required = false) String projectId
    ) {
        byte[] data = reportService.exportReportsExcel(ReportFilter.of(projectId, year, month));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("rapport-spi-ghomrassen-" + year + "-" + String.format("%02d", month) + ".xlsx")
                .build());
        return ResponseEntity.ok().headers(headers).body(data);
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam("year") Integer year,
            @RequestParam("month") Integer month,
            @RequestParam(required = false) String projectId
    ) {
        byte[] data = reportService.exportReportsPdf(ReportFilter.of(projectId, year, month));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("rapport-spi-ghomrassen-" + year + "-" + String.format("%02d", month) + ".pdf")
                .build());
        return ResponseEntity.ok().headers(headers).body(data);
    }
}
