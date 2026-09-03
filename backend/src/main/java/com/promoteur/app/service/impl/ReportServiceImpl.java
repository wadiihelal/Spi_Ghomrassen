package com.promoteur.app.service.impl;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.promoteur.app.dto.report.AmountByLabelDto;
import com.promoteur.app.dto.report.ClientStatementDto;
import com.promoteur.app.dto.report.CountAndTotal;
import com.promoteur.app.dto.report.PurchaseSummary;
import com.promoteur.app.dto.report.ReportFilter;
import com.promoteur.app.dto.report.ReportScope;
import com.promoteur.app.entity.Client;
import com.promoteur.app.entity.ClientAdvance;
import com.promoteur.app.entity.ClientPurchase;
import com.promoteur.app.entity.Expense;
import com.promoteur.app.entity.Project;
import com.promoteur.app.exception.ResourceNotFoundException;
import com.promoteur.app.repository.ClientAdvanceRepository;
import com.promoteur.app.repository.ClientPurchaseRepository;
import com.promoteur.app.repository.ClientRepository;
import com.promoteur.app.repository.ExpenseRepository;
import com.promoteur.app.repository.ProjectRepository;
import com.promoteur.app.repository.SupplierRepository;
import com.promoteur.app.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
// Reporting only ever reads (ARCH-03).
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final SupplierRepository supplierRepository;
    private final ExpenseRepository expenseRepository;
    private final ClientAdvanceRepository clientAdvanceRepository;
    private final ClientPurchaseRepository clientPurchaseRepository;

    @Override
    public ReportScope resolveScope(ReportFilter filter) {
        Long projectId = resolveProjectId(filter);
        return new ReportScope(
                projectId,
                projectLabel(projectId),
                periodLabel(filter),
                periodStart(filter),
                periodEnd(filter));
    }

    /**
     * @return the project the figures must cover: the explicit one, else the active project
     *         context, else {@code null} for every project. An explicit {@code projectId=ALL}
     *         and an unset context both yield {@code null}, but only the first is deliberate.
     */
    private Long resolveProjectId(ReportFilter filter) {
        if (filter.allProjects()) {
            return null;
        }
        if (filter.projectId() != null) {
            return filter.projectId();
        }
        return projectRepository.findFirstByActiveContextTrue().map(Project::getId).orElse(null);
    }

    private String projectLabel(Long projectId) {
        if (projectId == null) {
            return "Tous les projets";
        }
        return "Projet : " + projectRepository.findById(projectId)
                .map(Project::getName)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id " + projectId));
    }

    private String periodLabel(ReportFilter filter) {
        if (filter.year() == null) {
            return "Toutes périodes";
        }
        if (filter.month() == null) {
            return "Année " + filter.year();
        }
        String monthName = Month.of(filter.month()).getDisplayName(TextStyle.FULL, Locale.FRENCH);
        return "Période : " + monthName + " " + filter.year();
    }

    /** @return the first day covered, or {@code null} when no period was requested */
    private LocalDate periodStart(ReportFilter filter) {
        if (filter.year() == null) {
            return null;
        }
        return filter.month() == null
                ? LocalDate.of(filter.year(), 1, 1)
                : LocalDate.of(filter.year(), filter.month(), 1);
    }

    /** @return the last day covered, inclusive, or {@code null} when no period was requested */
    private LocalDate periodEnd(ReportFilter filter) {
        if (filter.year() == null) {
            return null;
        }
        if (filter.month() == null) {
            return LocalDate.of(filter.year(), 12, 31);
        }
        LocalDate start = periodStart(filter);
        return start.withDayOfMonth(start.lengthOfMonth());
    }

    @Override
    public Page<AmountByLabelDto> expensesByCategory(ReportFilter filter, Pageable pageable) {
        ReportScope scope = resolveScope(filter);
        return expenseRepository.sumByCategory(scope.projectId(), scope.dateFrom(), scope.dateTo(), pageable);
    }

    @Override
    public Page<AmountByLabelDto> expensesByProject(ReportFilter filter, Pageable pageable) {
        ReportScope scope = resolveScope(filter);
        return expenseRepository.sumByProject(scope.projectId(), scope.dateFrom(), scope.dateTo(), pageable);
    }

    @Override
    public Page<AmountByLabelDto> expensesByMonth(ReportFilter filter, Pageable pageable) {
        ReportScope scope = resolveScope(filter);
        return expenseRepository.sumByMonth(scope.projectId(), scope.dateFrom(), scope.dateTo(), pageable);
    }

    @Override
    public Page<AmountByLabelDto> purchasesByProject(ReportFilter filter, Pageable pageable) {
        ReportScope scope = resolveScope(filter);
        return clientPurchaseRepository.sumByProject(scope.projectId(), scope.dateFrom(), scope.dateTo(), pageable);
    }

    @Override
    public Page<AmountByLabelDto> advancesByPaymentMethod(ReportFilter filter, Pageable pageable) {
        ReportScope scope = resolveScope(filter);
        return clientAdvanceRepository.sumByPaymentMethod(scope.projectId(), scope.dateFrom(), scope.dateTo(), pageable);
    }

    /**
     * Statements for the clients in scope, aggregated and paginated by the database. When a
     * project is resolved, only the clients attached to that project are listed, and their
     * figures cover that project and period only.
     */
    @Override
    public Page<ClientStatementDto> clientStatements(ReportFilter filter, Pageable pageable) {
        return statements(null, resolveScope(filter), pageable);
    }

    @Override
    public ClientStatementDto clientStatement(Long clientId, ReportFilter filter) {
        return statements(clientId, resolveScope(filter), PageRequest.of(0, 1)).getContent().stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id " + clientId));
    }

    private Page<ClientStatementDto> statements(Long clientId, ReportScope scope, Pageable pageable) {
        return clientRepository.statements(clientId, scope.projectId(), scope.dateFrom(), scope.dateTo(), pageable);
    }

    /**
     * Dashboard summary. Six aggregate queries rather than one: JPQL has no FROM-less select, so
     * a single-row projection would mean a native query and give up PostgreSQL/H2 portability
     * for five saved round trips. What matters for PERF-01 is that the count is constant and
     * nothing is grouped in Java any more.
     */
    @Override
    public Map<String, Object> globalSummary(ReportFilter filter) {
        ReportScope scope = resolveScope(filter);

        CountAndTotal expenses = expenseRepository.countAndTotal(scope.projectId(), scope.dateFrom(), scope.dateTo());
        CountAndTotal advances = clientAdvanceRepository.countAndTotal(scope.projectId(), scope.dateFrom(), scope.dateTo());
        PurchaseSummary purchases = clientPurchaseRepository.summary(scope.projectId(), scope.dateFrom(), scope.dateTo());

        BigDecimal totalCollected = safe(advances.total()).add(safe(purchases.totalPaidDirectly()));
        BigDecimal totalPurchases = safe(purchases.totalContracted());

        Map<String, Object> result = new HashMap<>();
        result.put("projectId", scope.projectId());
        result.put("projectLabel", scope.projectLabel());
        result.put("periodLabel", scope.periodLabel());
        result.put("clients", scope.projectId() == null
                ? clientRepository.count()
                : clientRepository.countByProjectId(scope.projectId()));
        result.put("projects", scope.projectId() == null ? projectRepository.count() : 1L);
        // Les fournisseurs ne sont pas rattaches a un projet : le compteur reste global.
        result.put("suppliers", supplierRepository.count());
        result.put("expenses", expenses.count());
        result.put("clientAdvances", advances.count());
        result.put("clientPurchases", purchases.count());
        result.put("totalExpenses", safe(expenses.total()));
        result.put("totalAdvances", totalCollected);
        result.put("totalPurchases", totalPurchases);
        result.put("totalRemainingFromClients", totalPurchases.subtract(totalCollected));
        return result;
    }

    @Override
    public byte[] exportReportsExcel(ReportFilter filter) {
        ReportScope scope = resolveScope(filter);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            List<AmountByLabelDto> byCategory = allByCategory(scope);
            List<AmountByLabelDto> byProject = allByProject(scope);
            List<ClientStatementDto> statements = allStatements(scope);

            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            createAmountSheet(workbook, "Dépenses par catégorie", "Catégorie", byCategory, headerStyle, scope);
            createAmountSheet(workbook, "Dépenses par projet", "Projet", byProject, headerStyle, scope);
            createClientsSheet(workbook, statements, headerStyle, scope);

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Impossible de générer le fichier Excel de rapport", ex);
        }
    }

    @Override
    public byte[] exportReportsPdf(ReportFilter filter) {
        ReportScope scope = resolveScope(filter);
        List<AmountByLabelDto> byCategory = allByCategory(scope);
        List<AmountByLabelDto> byProject = allByProject(scope);
        List<ClientStatementDto> statements = allStatements(scope);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 24, 24, 24, 24);
            PdfWriter.getInstance(document, outputStream);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font textFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

            document.add(new Paragraph("SP Immobilière GHOMRASSEN - Rapport", titleFont));
            document.add(new Paragraph(scope.projectLabel(), sectionFont));
            document.add(new Paragraph(scope.periodLabel(), sectionFont));
            document.add(new Paragraph("Date d'édition : " + LocalDate.now(), textFont));
            document.add(new Paragraph(" "));

            document.add(new Paragraph("Dépenses par catégorie", sectionFont));
            document.add(buildTwoColumnPdfTable(byCategory, "Catégorie", "Total (DT)"));
            document.add(new Paragraph(" "));

            document.add(new Paragraph("Dépenses par projet", sectionFont));
            document.add(buildTwoColumnPdfTable(byProject, "Projet", "Total (DT)"));
            document.add(new Paragraph(" "));

            document.add(new Paragraph("Situation clients", sectionFont));
            document.add(buildClientsPdfTable(statements));
            document.add(new Paragraph(" "));

            document.close();
            return outputStream.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Impossible de générer le PDF de rapport", ex);
        }
    }

    private void createAmountSheet(Workbook workbook, String sheetName, String labelHeader, List<AmountByLabelDto> rows, CellStyle headerStyle, ReportScope scope) {
        Sheet sheet = workbook.createSheet(sheetName);
        writeCell(sheet.createRow(0), 0, scope.describe(), headerStyle);
        Row header = sheet.createRow(1);
        writeCell(header, 0, labelHeader, headerStyle);
        writeCell(header, 1, "Total (DT)", headerStyle);
        int rowIndex = 2;
        for (AmountByLabelDto row : rows) {
            Row excelRow = sheet.createRow(rowIndex++);
            excelRow.createCell(0).setCellValue(row.getLabel());
            excelRow.createCell(1).setCellValue(safe(row.getAmount()).doubleValue());
        }
        autosize(sheet, 2);
    }

    private void createClientsSheet(Workbook workbook, List<ClientStatementDto> rows, CellStyle headerStyle, ReportScope scope) {
        Sheet sheet = workbook.createSheet("Situation clients");
        writeCell(sheet.createRow(0), 0, scope.describe(), headerStyle);
        Row header = sheet.createRow(1);
        writeCell(header, 0, "Client", headerStyle);
        writeCell(header, 1, "Achats (DT)", headerStyle);
        writeCell(header, 2, "Paiements (DT)", headerStyle);
        writeCell(header, 3, "Reste à payer (DT)", headerStyle);
        int rowIndex = 2;
        for (ClientStatementDto row : rows) {
            Row excelRow = sheet.createRow(rowIndex++);
            excelRow.createCell(0).setCellValue(row.getClientName());
            excelRow.createCell(1).setCellValue(safe(row.getTotalPurchases()).doubleValue());
            excelRow.createCell(2).setCellValue(safe(row.getTotalAdvances()).doubleValue());
            excelRow.createCell(3).setCellValue(safe(row.getRemainingToPay()).doubleValue());
        }
        autosize(sheet, 4);
    }

    private PdfPTable buildTwoColumnPdfTable(List<AmountByLabelDto> rows, String firstHeader, String secondHeader) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{4f, 2f});
        table.setWidthPercentage(100);
        table.addCell(headerCell(firstHeader));
        table.addCell(headerCell(secondHeader));
        for (AmountByLabelDto row : rows) {
            table.addCell(bodyCell(defaultString(row.getLabel())));
            table.addCell(bodyCell(formatMoney(row.getAmount())));
        }
        return table;
    }

    private PdfPTable buildClientsPdfTable(List<ClientStatementDto> rows) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{4f, 2f, 2f, 2f});
        table.setWidthPercentage(100);
        table.addCell(headerCell("Client"));
        table.addCell(headerCell("Achats (DT)"));
        table.addCell(headerCell("Paiements (DT)"));
        table.addCell(headerCell("Reste (DT)"));
        for (ClientStatementDto row : rows) {
            table.addCell(bodyCell(defaultString(row.getClientName())));
            table.addCell(bodyCell(formatMoney(row.getTotalPurchases())));
            table.addCell(bodyCell(formatMoney(row.getTotalAdvances())));
            table.addCell(bodyCell(formatMoney(row.getRemainingToPay())));
        }
        return table;
    }

    private PdfPCell headerCell(String value) {
        PdfPCell cell = new PdfPCell(new Phrase(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
        cell.setPadding(6f);
        return cell;
    }

    private PdfPCell bodyCell(String value) {
        PdfPCell cell = new PdfPCell(new Phrase(value, FontFactory.getFont(FontFactory.HELVETICA, 8)));
        cell.setPadding(5f);
        return cell;
    }

    /** A whole export is not paginated: the aggregate is read in one page. */
    private List<AmountByLabelDto> allByCategory(ReportScope scope) {
        return expenseRepository.sumByCategory(scope.projectId(), scope.dateFrom(), scope.dateTo(),
                Pageable.unpaged()).getContent();
    }

    private List<AmountByLabelDto> allByProject(ReportScope scope) {
        return expenseRepository.sumByProject(scope.projectId(), scope.dateFrom(), scope.dateTo(),
                Pageable.unpaged()).getContent();
    }

    private List<ClientStatementDto> allStatements(ReportScope scope) {
        return statements(null, scope, Pageable.unpaged()).getContent();
    }

    private void writeCell(Row row, int columnIndex, String value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        cell.setCellValue(value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private void autosize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String formatMoney(BigDecimal value) {
        return safe(value).setScale(3, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String defaultString(String value) {
        return value == null ? "-" : value;
    }
}
