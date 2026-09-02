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
import com.promoteur.app.entity.Client;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final SupplierRepository supplierRepository;
    private final ExpenseRepository expenseRepository;
    private final ClientAdvanceRepository clientAdvanceRepository;
    private final ClientPurchaseRepository clientPurchaseRepository;

    @Override
    public Page<AmountByLabelDto> expensesByCategory(Pageable pageable) {
        return toPage(expensesByCategoryList(), pageable);
    }

    private List<AmountByLabelDto> expensesByCategoryList() {
        return expenseRepository.findAll().stream()
                .collect(Collectors.groupingBy(e -> e.getCategory().getName(),
                        Collectors.mapping(e -> safe(e.getAmountTtc()),
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))))
                .entrySet().stream()
                .map(e -> new AmountByLabelDto(e.getKey(), e.getValue()))
                .sorted((a, b) -> safe(b.getAmount()).compareTo(safe(a.getAmount())))
                .toList();
    }

    @Override
    public Page<AmountByLabelDto> expensesByProject(Pageable pageable) {
        return toPage(expensesByProjectList(), pageable);
    }

    private List<AmountByLabelDto> expensesByProjectList() {
        return expenseRepository.findAll().stream()
                .collect(Collectors.groupingBy(e -> e.getProject().getName(),
                        Collectors.mapping(e -> safe(e.getAmountTtc()),
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))))
                .entrySet().stream()
                .map(e -> new AmountByLabelDto(e.getKey(), e.getValue()))
                .sorted((a, b) -> safe(b.getAmount()).compareTo(safe(a.getAmount())))
                .toList();
    }

    @Override
    public Page<ClientStatementDto> clientStatements(Pageable pageable) {
        return toPage(clientStatementsList(), pageable);
    }

    private List<ClientStatementDto> clientStatementsList() {
        return clientRepository.findAll().stream()
                .map(client -> buildStatement(client.getId(), client.getFullName()))
                .sorted((a, b) -> safe(b.getRemainingToPay()).compareTo(safe(a.getRemainingToPay())))
                .toList();
    }

    @Override
    public ClientStatementDto clientStatement(Long clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id " + clientId));
        return buildStatement(client.getId(), client.getFullName());
    }

    @Override
    public Map<String, Object> globalSummary() {
        BigDecimal totalExpenses = expenseRepository.findAll().stream()
                .map(e -> safe(e.getAmountTtc()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCollected = clientAdvanceRepository.findAll().stream()
                .map(a -> safe(a.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        totalCollected = totalCollected.add(clientPurchaseRepository.findAll().stream()
                .map(p -> safe(p.getPaidAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        BigDecimal totalPurchases = clientPurchaseRepository.findAll().stream()
                .map(p -> safe(p.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new HashMap<>();
        result.put("clients", clientRepository.count());
        result.put("projects", projectRepository.count());
        result.put("suppliers", supplierRepository.count());
        result.put("expenses", expenseRepository.count());
        result.put("clientAdvances", clientAdvanceRepository.count());
        result.put("clientPurchases", clientPurchaseRepository.count());
        result.put("totalExpenses", totalExpenses);
        result.put("totalAdvances", totalCollected);
        result.put("totalPurchases", totalPurchases);
        result.put("totalRemainingFromClients", totalPurchases.subtract(totalCollected));
        return result;
    }

    @Override
    public byte[] exportReportsExcel(Integer year, Integer month) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            List<AmountByLabelDto> byCategory = expensesByCategoryList();
            List<AmountByLabelDto> byProject = expensesByProjectList();
            List<ClientStatementDto> statements = clientStatementsList();

            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            createAmountSheet(workbook, "Dépenses par catégorie", "Catégorie", byCategory, headerStyle);
            createAmountSheet(workbook, "Dépenses par projet", "Projet", byProject, headerStyle);
            createClientsSheet(workbook, statements, headerStyle);

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Impossible de générer le fichier Excel de rapport", ex);
        }
    }

    @Override
    public byte[] exportReportsPdf(Integer year, Integer month) {
        List<AmountByLabelDto> byCategory = expensesByCategoryList();
        List<AmountByLabelDto> byProject = expensesByProjectList();
        List<ClientStatementDto> statements = clientStatementsList();

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 24, 24, 24, 24);
            PdfWriter.getInstance(document, outputStream);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font textFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

            document.add(new Paragraph("SP Immobilière GHOMRASSEN - Rapport global", titleFont));
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

    private void createAmountSheet(Workbook workbook, String sheetName, String labelHeader, List<AmountByLabelDto> rows, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet(sheetName);
        Row header = sheet.createRow(0);
        writeCell(header, 0, labelHeader, headerStyle);
        writeCell(header, 1, "Total (DT)", headerStyle);
        int rowIndex = 1;
        for (AmountByLabelDto row : rows) {
            Row excelRow = sheet.createRow(rowIndex++);
            excelRow.createCell(0).setCellValue(row.getLabel());
            excelRow.createCell(1).setCellValue(safe(row.getAmount()).doubleValue());
        }
        autosize(sheet, 2);
    }

    private void createClientsSheet(Workbook workbook, List<ClientStatementDto> rows, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("Situation clients");
        Row header = sheet.createRow(0);
        writeCell(header, 0, "Client", headerStyle);
        writeCell(header, 1, "Achats (DT)", headerStyle);
        writeCell(header, 2, "Paiements (DT)", headerStyle);
        writeCell(header, 3, "Reste à payer (DT)", headerStyle);
        int rowIndex = 1;
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

    private <T> Page<T> toPage(List<T> items, Pageable pageable) {
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), items.size());
        if (start >= items.size()) {
            return new PageImpl<>(List.of(), pageable, items.size());
        }
        return new PageImpl<>(items.subList(start, end), pageable, items.size());
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

    private ClientStatementDto buildStatement(Long clientId, String clientName) {
        BigDecimal totalPurchases = clientPurchaseRepository.findByClientId(clientId).stream()
                .map(p -> safe(p.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCollected = clientAdvanceRepository.findByClientId(clientId).stream()
                .map(a -> safe(a.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        totalCollected = totalCollected.add(clientPurchaseRepository.findByClientId(clientId).stream()
                .map(p -> safe(p.getPaidAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        return ClientStatementDto.builder()
                .clientId(clientId)
                .clientName(clientName)
                .totalPurchases(totalPurchases)
                .totalAdvances(totalCollected)
                .remainingToPay(totalPurchases.subtract(totalCollected))
                .build();
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
