package com.promoteur.app.report;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.promoteur.app.advance.ClientAdvanceRepository;
import com.promoteur.app.client.ClientRepository;
import com.promoteur.app.config.CompanyProperties;
import com.promoteur.app.dashboard.DashboardSummaryResponse;
import com.promoteur.app.expense.ExpenseRepository;
import com.promoteur.app.project.Project;
import com.promoteur.app.project.ProjectRepository;
import com.promoteur.app.purchase.ClientPurchaseRepository;
import com.promoteur.app.shared.PdfLetterhead;
import com.promoteur.app.shared.ResourceNotFoundException;
import com.promoteur.app.supplier.SupplierRepository;
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
import java.util.List;
import java.util.Locale;

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
    private final CompanyProperties company;

    /**
     * « Projet : X » becomes « X »: the label is printed once, by the fact column.
     */
    private static String stripLeadIn(String label) {
        if (label == null) {
            return "-";
        }
        int colon = label.indexOf(':');
        return colon >= 0 && colon < 12 ? label.substring(colon + 1).trim() : label;
    }

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
     * context, else {@code null} for every project. An explicit {@code projectId=ALL}
     * and an unset context both yield {@code null}, but only the first is deliberate.
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

    /**
     * @return the first day covered, or {@code null} when no period was requested
     */
    private LocalDate periodStart(ReportFilter filter) {
        if (filter.year() == null) {
            return null;
        }
        return filter.month() == null
                ? LocalDate.of(filter.year(), 1, 1)
                : LocalDate.of(filter.year(), filter.month(), 1);
    }

    /**
     * @return the last day covered, inclusive, or {@code null} when no period was requested
     */
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
        return expenseRepository.sumByMonth(scope.projectId(), scope.dateFrom(), scope.dateTo(), pageable)
                .map(row -> new AmountByLabelDto(row.label(), row.amount()));
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
    public DashboardSummaryResponse globalSummary(ReportFilter filter) {
        ReportScope scope = resolveScope(filter);

        CountAndTotal expenses = expenseRepository.countAndTotal(scope.projectId(), scope.dateFrom(), scope.dateTo());
        CountAndTotal advances = clientAdvanceRepository.countAndTotal(scope.projectId(), scope.dateFrom(), scope.dateTo());
        PurchaseSummary purchases = clientPurchaseRepository.summary(scope.projectId(), scope.dateFrom(), scope.dateTo());

        BigDecimal totalCollected = safe(advances.total()).add(safe(purchases.totalPaidDirectly()));
        BigDecimal totalPurchases = safe(purchases.totalContracted());

        return new DashboardSummaryResponse(
                scope.projectId(),
                scope.projectLabel(),
                scope.periodLabel(),
                scope.projectId() == null ? clientRepository.count() : clientRepository.countByProjectId(scope.projectId()),
                scope.projectId() == null ? projectRepository.count() : 1L,
                // Les fournisseurs ne sont pas rattaches a un projet : le compteur reste global.
                supplierRepository.count(),
                expenses.count(),
                advances.count(),
                purchases.count(),
                safe(expenses.total()),
                totalCollected,
                totalPurchases,
                totalPurchases.subtract(totalCollected));
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
            Document document = new Document(PageSize.A4, 42f, 42f, 42f, 42f);
            PdfWriter.getInstance(document, outputStream);
            document.open();

            // Same letterhead as the receipt and the statement (UX-06): one company, one paper.
            // The scope labels carry their own « Projet : » / « Période : » lead-ins for the
            // Excel header; the letterhead's fact column already names them.
            PdfLetterhead.open(document, company, "Rapport de gestion", stripLeadIn(scope.periodLabel()));
            document.add(PdfLetterhead.facts(new String[][]{
                    {"Périmètre", stripLeadIn(scope.projectLabel())},
                    {"Période", stripLeadIn(scope.periodLabel())}
            }));

            document.add(PdfLetterhead.heading("Dépenses par catégorie"));
            document.add(buildTwoColumnPdfTable(byCategory, "Catégorie", "Total"));

            document.add(PdfLetterhead.heading("Dépenses par projet"));
            document.add(buildTwoColumnPdfTable(byProject, "Projet", "Total"));

            document.add(PdfLetterhead.heading("Mouvements des acquéreurs sur la période"));
            document.add(buildClientsPdfTable(statements));

            document.add(PdfLetterhead.note(
                    "Chiffres arrêtés à la date d'édition sur les écritures enregistrées pour la période."));
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

    private PdfPTable buildTwoColumnPdfTable(List<AmountByLabelDto> rows, String firstHeader, String secondHeader) {
        PdfPTable table = new PdfPTable(new float[]{4f, 2f});
        table.setWidthPercentage(100);
        table.addCell(PdfLetterhead.headerCell(firstHeader));
        table.addCell(PdfLetterhead.headerCell(secondHeader));
        BigDecimal total = BigDecimal.ZERO;
        for (AmountByLabelDto row : rows) {
            table.addCell(PdfLetterhead.bodyCell(defaultString(row.getLabel())));
            table.addCell(PdfLetterhead.amountCell(PdfLetterhead.money(row.getAmount())));
            total = total.add(safe(row.getAmount()));
        }
        if (rows.isEmpty()) {
            table.addCell(PdfLetterhead.bodyCell("Aucune écriture sur la période"));
            table.addCell(PdfLetterhead.amountCell(PdfLetterhead.money(BigDecimal.ZERO)));
        }
        table.addCell(PdfLetterhead.totalCell("Total", Element.ALIGN_LEFT));
        table.addCell(PdfLetterhead.totalCell(PdfLetterhead.money(total), Element.ALIGN_RIGHT));
        return table;
    }

    private PdfPTable buildClientsPdfTable(List<ClientStatementDto> rows) {
        PdfPTable table = new PdfPTable(new float[]{4f, 2f, 2f, 2f});
        table.setWidthPercentage(100);
        table.addCell(PdfLetterhead.headerCell("Client"));
        table.addCell(PdfLetterhead.headerCell("Contracté"));
        table.addCell(PdfLetterhead.headerCell("Encaissé"));
        table.addCell(PdfLetterhead.headerCell("Reste à payer"));
        BigDecimal contracted = BigDecimal.ZERO;
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal remaining = BigDecimal.ZERO;
        for (ClientStatementDto row : rows) {
            table.addCell(PdfLetterhead.bodyCell(defaultString(row.getClientName())));
            table.addCell(PdfLetterhead.amountCell(PdfLetterhead.money(row.getTotalPurchases())));
            table.addCell(PdfLetterhead.amountCell(PdfLetterhead.money(row.getTotalAdvances())));
            table.addCell(PdfLetterhead.amountCell(PdfLetterhead.money(row.getRemainingToPay())));
            contracted = contracted.add(safe(row.getTotalPurchases()));
            collected = collected.add(safe(row.getTotalAdvances()));
            remaining = remaining.add(safe(row.getRemainingToPay()));
        }
        if (rows.isEmpty()) {
            table.addCell(PdfLetterhead.bodyCell("Aucun mouvement d'acquéreur sur la période"));
            table.addCell(PdfLetterhead.amountCell(PdfLetterhead.money(BigDecimal.ZERO)));
            table.addCell(PdfLetterhead.amountCell(PdfLetterhead.money(BigDecimal.ZERO)));
            table.addCell(PdfLetterhead.amountCell(PdfLetterhead.money(BigDecimal.ZERO)));
        }
        table.addCell(PdfLetterhead.totalCell("Total", Element.ALIGN_LEFT));
        table.addCell(PdfLetterhead.totalCell(PdfLetterhead.money(contracted), Element.ALIGN_RIGHT));
        table.addCell(PdfLetterhead.totalCell(PdfLetterhead.money(collected), Element.ALIGN_RIGHT));
        table.addCell(PdfLetterhead.totalCell(PdfLetterhead.money(remaining), Element.ALIGN_RIGHT));
        return table;
    }

    /**
     * A whole export is not paginated: the aggregate is read in one page.
     */
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


    private String defaultString(String value) {
        return value == null ? "-" : value;
    }
}
