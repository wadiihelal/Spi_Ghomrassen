package com.promoteur.app.service.impl;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.promoteur.app.config.CompanyProperties;

import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The look the three printed documents share (UX-06).
 *
 * <p>One letterhead, one type scale and one money format, so a receipt, a statement and a VAT
 * recap read as paper from the same company. Kept out of the services themselves: they decide
 * what a document says, this decides how it looks.</p>
 */
final class PdfLetterhead {

    /** Navy of the application chrome, so the paper matches the screen. */
    private static final Color INK = new Color(15, 32, 60);
    private static final Color MUTED = new Color(105, 120, 140);
    private static final Color RULE = new Color(205, 167, 95);
    private static final Color BAND = new Color(243, 246, 250);

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final DecimalFormat MONEY = PdfLetterhead.moneyFormat();

    /**
     * French figures, but with an ordinary space between thousands: the locale's narrow no-break
     * space is not in the PDF font's encoding and was silently dropped, running the digits
     * together.
     */
    private static DecimalFormat moneyFormat() {
        final DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.000", symbols);
    }

    private PdfLetterhead() {
    }

    static Font title() {
        return PdfLetterhead.font(FontFactory.HELVETICA_BOLD, 16, INK);
    }

    static Font section() {
        return PdfLetterhead.font(FontFactory.HELVETICA_BOLD, 11, INK);
    }

    static Font body() {
        return PdfLetterhead.font(FontFactory.HELVETICA, 10, INK);
    }

    static Font bodyBold() {
        return PdfLetterhead.font(FontFactory.HELVETICA_BOLD, 10, INK);
    }

    static Font muted() {
        return PdfLetterhead.font(FontFactory.HELVETICA, 9, MUTED);
    }

    private static Font font(final String name, final float size, final Color colour) {
        // Cp1252 keeps the French accents: the default encoding drops them.
        final Font font = FontFactory.getFont(name, "Cp1252", true, size);
        font.setColor(colour);
        return font;
    }

    /** Money as a Tunisian promoter writes it: three decimals, space as thousands separator. */
    static String money(final BigDecimal amount) {
        final BigDecimal value = (amount == null ? BigDecimal.ZERO : amount).setScale(3, RoundingMode.HALF_UP);
        return MONEY.format(value) + " DT";
    }

    static String date(final LocalDate value) {
        return value == null ? "-" : value.format(PdfLetterhead.DATE);
    }

    /**
     * Writes the company block, the document title and its reference, then a brass rule.
     *
     * @param reference the document's own number, printed to the right of the title
     */
    static void open(final Document document, final CompanyProperties company,
                     final String documentTitle, final String reference) {
        final PdfPTable head = new PdfPTable(new float[]{3f, 2f});
        head.setWidthPercentage(100);
        head.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        final Paragraph identity = new Paragraph();
        identity.add(new Phrase(company.name() == null ? "" : company.name(), PdfLetterhead.title()));
        PdfLetterhead.appendLine(identity, company, company.legalForm());
        PdfLetterhead.appendLine(identity, company, company.address());
        if (company.has(company.taxId())) {
            identity.add(new Phrase("\nMatricule fiscal : " + company.taxId(), PdfLetterhead.muted()));
        }
        PdfLetterhead.appendLine(identity, company, company.phone());
        PdfLetterhead.appendLine(identity, company, company.email());
        head.addCell(PdfLetterhead.plain(identity));

        final Paragraph label = new Paragraph();
        label.setAlignment(Element.ALIGN_RIGHT);
        label.add(new Phrase(documentTitle.toUpperCase(Locale.FRENCH), PdfLetterhead.section()));
        if (reference != null && !reference.isBlank()) {
            label.add(new Phrase("\nN° " + reference, PdfLetterhead.bodyBold()));
        }
        label.add(new Phrase("\nÉdité le " + LocalDate.now().format(PdfLetterhead.DATE), PdfLetterhead.muted()));
        head.addCell(PdfLetterhead.plain(label));

        document.add(head);
        document.add(PdfLetterhead.rule());
    }

    private static void appendLine(final Paragraph target, final CompanyProperties company, final String value) {
        if (company.has(value)) {
            target.add(new Phrase("\n" + value, PdfLetterhead.muted()));
        }
    }

    private static PdfPCell plain(final Paragraph content) {
        final PdfPCell cell = new PdfPCell(content);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0f);
        return cell;
    }

    /** The single brass rule that separates the letterhead from the document's body. */
    static PdfPTable rule() {
        final PdfPTable rule = new PdfPTable(1);
        rule.setWidthPercentage(100);
        rule.setSpacingBefore(8f);
        rule.setSpacingAfter(14f);
        final PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColorBottom(RULE);
        cell.setBorderWidthBottom(1.4f);
        cell.setFixedHeight(1f);
        rule.addCell(cell);
        return rule;
    }

    static Paragraph heading(final String text) {
        final Paragraph heading = new Paragraph(text, PdfLetterhead.section());
        heading.setSpacingBefore(12f);
        heading.setSpacingAfter(6f);
        return heading;
    }

    /** A label and value pair, the shape every identity block on these documents uses. */
    static PdfPTable facts(final String[][] rows) {
        final PdfPTable table = new PdfPTable(new float[]{1.2f, 2.8f});
        table.setWidthPercentage(100);
        for (final String[] row : rows) {
            table.addCell(PdfLetterhead.factCell(row[0], PdfLetterhead.muted()));
            table.addCell(PdfLetterhead.factCell(row[1], PdfLetterhead.body()));
        }
        return table;
    }

    private static PdfPCell factCell(final String value, final Font font) {
        final PdfPCell cell = new PdfPCell(new Phrase(value == null ? "-" : value, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingBottom(4f);
        return cell;
    }

    static PdfPCell headerCell(final String value) {
        final PdfPCell cell = new PdfPCell(new Phrase(value, PdfLetterhead.bodyBold()));
        cell.setPadding(6f);
        cell.setBackgroundColor(BAND);
        cell.setBorderColor(new Color(225, 231, 239));
        return cell;
    }

    static PdfPCell bodyCell(final String value) {
        return PdfLetterhead.bodyCell(value, Element.ALIGN_LEFT);
    }

    static PdfPCell amountCell(final String value) {
        return PdfLetterhead.bodyCell(value, Element.ALIGN_RIGHT);
    }

    static PdfPCell bodyCell(final String value, final int alignment) {
        final PdfPCell cell = new PdfPCell(new Phrase(value == null ? "-" : value, PdfLetterhead.body()));
        cell.setPadding(6f);
        cell.setHorizontalAlignment(alignment);
        cell.setBorderColor(new Color(225, 231, 239));
        return cell;
    }

    static PdfPCell totalCell(final String value, final int alignment) {
        final PdfPCell cell = new PdfPCell(new Phrase(value == null ? "-" : value, PdfLetterhead.bodyBold()));
        cell.setPadding(6f);
        cell.setHorizontalAlignment(alignment);
        cell.setBackgroundColor(BAND);
        cell.setBorderColor(new Color(225, 231, 239));
        return cell;
    }

    /** The closing note every document carries, in smaller muted type. */
    static Paragraph note(final String text) {
        final Paragraph note = new Paragraph(text, PdfLetterhead.muted());
        note.setSpacingBefore(16f);
        return note;
    }

    /** Two signature lines, the way a paper receipt is countersigned. */
    static PdfPTable signatures(final String left, final String right) {
        final PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(28f);
        table.addCell(PdfLetterhead.signatureCell(left));
        table.addCell(PdfLetterhead.signatureCell(right));
        return table;
    }

    private static PdfPCell signatureCell(final String label) {
        final PdfPCell cell = new PdfPCell(new Phrase(label, PdfLetterhead.muted()));
        cell.setBorder(Rectangle.TOP);
        cell.setBorderColorTop(new Color(205, 213, 224));
        cell.setPaddingTop(6f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        return cell;
    }
}
