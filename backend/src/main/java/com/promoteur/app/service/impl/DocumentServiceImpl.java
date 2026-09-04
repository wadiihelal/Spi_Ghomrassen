package com.promoteur.app.service.impl;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.promoteur.app.config.CompanyProperties;
import com.promoteur.app.dto.PurchaseTotals;
import com.promoteur.app.dto.VatByRate;
import com.promoteur.app.entity.Client;
import com.promoteur.app.entity.ClientAdvance;
import com.promoteur.app.entity.ClientPurchase;
import com.promoteur.app.exception.ResourceNotFoundException;
import com.promoteur.app.repository.ClientAdvanceRepository;
import com.promoteur.app.repository.ClientPurchaseRepository;
import com.promoteur.app.repository.ClientRepository;
import com.promoteur.app.repository.ExpenseRepository;
import com.promoteur.app.repository.ProjectRepository;
import com.promoteur.app.repository.SupplierInvoiceRepository;
import com.promoteur.app.service.AmountInWordsService;
import com.promoteur.app.service.ClientPurchaseCalculationService;
import com.promoteur.app.service.DocumentService;
import com.promoteur.app.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The three printed documents (UX-06).
 *
 * <p>Read-only: a document reports what is stored and never changes it, so printing a receipt
 * twice cannot alter a balance. The figures come from the same services the screens use, which
 * is why the paper and the screen agree.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentServiceImpl implements DocumentService {

    /** Page margins, in points; roughly 15 mm all round. */
    private static final float MARGIN = 42f;

    private final ClientRepository clientRepository;
    private final ClientAdvanceRepository clientAdvanceRepository;
    private final ClientPurchaseRepository clientPurchaseRepository;
    private final ExpenseRepository expenseRepository;
    private final SupplierInvoiceRepository supplierInvoiceRepository;
    private final ProjectRepository projectRepository;
    private final ClientPurchaseCalculationService calculationService;
    private final AmountInWordsService amountInWordsService;
    private final MessageService messageService;
    private final CompanyProperties company;

    @Override
    public byte[] paymentReceipt(final Long advanceId) {
        final ClientAdvance advance = this.clientAdvanceRepository.findById(advanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Client advance not found with id " + advanceId));
        final Client client = advance.getClient();

        return this.render(document -> {
            PdfLetterhead.open(document, this.company, "Reçu de paiement", advance.getReference());

            document.add(PdfLetterhead.facts(new String[][]{
                    {"Reçu de", client.getFullName()},
                    {"CIN / matricule", DocumentServiceImpl.orDash(client.getCinOrFiscalId())},
                    {"Adresse", DocumentServiceImpl.orDash(client.getAddress())},
                    {"Projet", advance.getProject().getName()},
                    {"Lot", advance.getApartment().getApartmentNumber()
                            + " (" + advance.getApartment().getApartmentType() + ")"},
                    {"Date du règlement", PdfLetterhead.date(advance.getAdvanceDate())},
                    {"Mode de règlement", this.paymentMethodLabel(advance)}
            }));

            // The amount twice: in figures, then in words, as a receipt must state it.
            final Paragraph amount = new Paragraph();
            amount.setSpacingBefore(16f);
            amount.add(new Phrase("Montant reçu : ", PdfLetterhead.body()));
            amount.add(new Phrase(PdfLetterhead.money(advance.getAmount()), PdfLetterhead.title()));
            document.add(amount);

            final Paragraph words = new Paragraph();
            words.add(new Phrase("Arrêté la présente quittance à la somme de "
                    + this.amountInWordsService.spell(advance.getAmount()) + ".", PdfLetterhead.body()));
            words.setSpacingBefore(4f);
            document.add(words);

            // Where the contract stands once this payment is in: the question the buyer asks next.
            this.appendContractPosition(document, advance);

            if (advance.getNotes() != null && !advance.getNotes().isBlank()) {
                document.add(PdfLetterhead.heading("Observations"));
                document.add(new Paragraph(advance.getNotes(), PdfLetterhead.body()));
            }

            document.add(PdfLetterhead.note(
                    "Ce reçu vaut quittance du seul montant indiqué ci-dessus. Il ne préjuge pas du solde "
                            + "restant dû au titre du contrat de vente."));
            document.add(PdfLetterhead.signatures("Le client", "Pour " + this.company.name()));
        });
    }

    /** The contract's position, or a line saying there is no contract on the lot yet. */
    private void appendContractPosition(final Document document, final ClientAdvance advance) {
        final ClientPurchase purchase = this.clientPurchaseRepository
                .findByApartmentId(advance.getApartment().getId()).orElse(null);
        document.add(PdfLetterhead.heading("Situation du contrat"));

        if (purchase == null) {
            document.add(new Paragraph(
                    "Aucun contrat de vente n'est encore enregistré pour ce lot ; le montant ci-dessus "
                            + "est un acompte de réservation.", PdfLetterhead.body()));
            return;
        }

        final BigDecimal advances = this.clientAdvanceRepository.findByApartmentId(
                        advance.getApartment().getId()).stream()
                .map(ClientAdvance::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        final PurchaseTotals totals = this.calculationService.totals(
                purchase.getTotalAmount(), purchase.getPaidAmount(), advances);

        final PdfPTable table = new PdfPTable(new float[]{3f, 2f});
        table.setWidthPercentage(100);
        table.addCell(PdfLetterhead.headerCell("Contrat " + purchase.getReference()));
        table.addCell(PdfLetterhead.headerCell("Montant"));
        table.addCell(PdfLetterhead.bodyCell("Prix de vente"));
        table.addCell(PdfLetterhead.amountCell(PdfLetterhead.money(purchase.getTotalAmount())));
        table.addCell(PdfLetterhead.bodyCell("Total encaissé à ce jour"));
        table.addCell(PdfLetterhead.amountCell(PdfLetterhead.money(totals.collectedAmount())));
        table.addCell(PdfLetterhead.totalCell("Reste à payer", Element.ALIGN_LEFT));
        table.addCell(PdfLetterhead.totalCell(PdfLetterhead.money(totals.remainingAmount()), Element.ALIGN_RIGHT));
        document.add(table);
    }

    @Override
    public byte[] clientStatement(final Long clientId) {
        final Client client = this.clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id " + clientId));
        final List<ClientPurchase> purchases =
                this.clientPurchaseRepository.findByClientIdOrderByPurchaseDateAsc(clientId);
        final List<ClientAdvance> advances =
                this.clientAdvanceRepository.findByClientIdOrderByAdvanceDateAsc(clientId);

        final BigDecimal contracted = purchases.stream()
                .map(ClientPurchase::getTotalAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        final BigDecimal direct = purchases.stream()
                .map(ClientPurchase::getPaidAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        final BigDecimal collectedInAdvances = advances.stream()
                .map(ClientAdvance::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        final BigDecimal collected = direct.add(collectedInAdvances);
        final BigDecimal remaining = contracted.subtract(collected).max(BigDecimal.ZERO);

        return this.render(document -> {
            // A statement is not a legal instrument, so it carries the buyer's own number rather than
            // consuming a document sequence; padded so the documents file in order.
            PdfLetterhead.open(document, this.company, "Situation de compte",
                    String.format("CLI-%04d", client.getId()));

            document.add(PdfLetterhead.facts(new String[][]{
                    {"Client", client.getFullName()},
                    {"CIN / matricule", DocumentServiceImpl.orDash(client.getCinOrFiscalId())},
                    {"Téléphone", DocumentServiceImpl.orDash(client.getPhone())},
                    {"Adresse", DocumentServiceImpl.orDash(client.getAddress())},
                    {"Arrêtée au", PdfLetterhead.date(LocalDate.now())}
            }));

            document.add(PdfLetterhead.heading("Contrats de vente"));
            document.add(DocumentServiceImpl.purchaseTable(purchases, contracted));

            document.add(PdfLetterhead.heading("Règlements encaissés"));
            document.add(DocumentServiceImpl.advanceTable(advances, collectedInAdvances, direct, collected));

            document.add(PdfLetterhead.heading("Solde"));
            final PdfPTable balance = new PdfPTable(new float[]{3f, 2f});
            balance.setWidthPercentage(100);
            balance.addCell(PdfLetterhead.bodyCell("Total contracté"));
            balance.addCell(PdfLetterhead.amountCell(PdfLetterhead.money(contracted)));
            balance.addCell(PdfLetterhead.bodyCell("Total encaissé"));
            balance.addCell(PdfLetterhead.amountCell(PdfLetterhead.money(collected)));
            balance.addCell(PdfLetterhead.totalCell("Reste à payer", Element.ALIGN_LEFT));
            balance.addCell(PdfLetterhead.totalCell(PdfLetterhead.money(remaining), Element.ALIGN_RIGHT));
            document.add(balance);

            final Paragraph words = new Paragraph(
                    "Soit un solde de " + this.amountInWordsService.spell(remaining) + ".",
                    PdfLetterhead.body());
            words.setSpacingBefore(6f);
            document.add(words);

            document.add(PdfLetterhead.note(
                    "Situation établie à partir des contrats et règlements enregistrés à la date d'édition. "
                            + "Toute réclamation doit être adressée dans les huit jours."));
        });
    }

    private static PdfPTable purchaseTable(final List<ClientPurchase> purchases, final BigDecimal total) {
        final PdfPTable table = new PdfPTable(new float[]{1.6f, 1.1f, 2.2f, 1.6f});
        table.setWidthPercentage(100);
        table.addCell(PdfLetterhead.headerCell("Référence"));
        table.addCell(PdfLetterhead.headerCell("Date"));
        table.addCell(PdfLetterhead.headerCell("Lot"));
        table.addCell(PdfLetterhead.headerCell("Montant"));

        if (purchases.isEmpty()) {
            table.addCell(PdfLetterhead.bodyCell("Aucun contrat enregistré"));
            table.addCell(PdfLetterhead.bodyCell("-"));
            table.addCell(PdfLetterhead.bodyCell("-"));
            table.addCell(PdfLetterhead.amountCell(PdfLetterhead.money(BigDecimal.ZERO)));
            return table;
        }

        for (final ClientPurchase purchase : purchases) {
            table.addCell(PdfLetterhead.bodyCell(purchase.getReference()));
            table.addCell(PdfLetterhead.bodyCell(PdfLetterhead.date(purchase.getPurchaseDate())));
            table.addCell(PdfLetterhead.bodyCell(purchase.getApartment().getApartmentNumber()
                    + " · " + purchase.getProject().getName()));
            table.addCell(PdfLetterhead.amountCell(PdfLetterhead.money(purchase.getTotalAmount())));
        }
        table.addCell(PdfLetterhead.totalCell("Total contracté", Element.ALIGN_LEFT));
        table.addCell(PdfLetterhead.totalCell("", Element.ALIGN_LEFT));
        table.addCell(PdfLetterhead.totalCell("", Element.ALIGN_LEFT));
        table.addCell(PdfLetterhead.totalCell(PdfLetterhead.money(total), Element.ALIGN_RIGHT));
        return table;
    }

    /**
     * The payments, plus the amount paid on the contracts themselves: without that line the
     * statement's total would not match its balance.
     */
    private static PdfPTable advanceTable(final List<ClientAdvance> advances, final BigDecimal advanceTotal,
                                          final BigDecimal direct, final BigDecimal collected) {
        final PdfPTable table = new PdfPTable(new float[]{1.6f, 1.1f, 2.2f, 1.6f});
        table.setWidthPercentage(100);
        table.addCell(PdfLetterhead.headerCell("Référence"));
        table.addCell(PdfLetterhead.headerCell("Date"));
        table.addCell(PdfLetterhead.headerCell("Lot"));
        table.addCell(PdfLetterhead.headerCell("Montant"));

        for (final ClientAdvance advance : advances) {
            table.addCell(PdfLetterhead.bodyCell(advance.getReference()));
            table.addCell(PdfLetterhead.bodyCell(PdfLetterhead.date(advance.getAdvanceDate())));
            table.addCell(PdfLetterhead.bodyCell(advance.getApartment().getApartmentNumber()));
            table.addCell(PdfLetterhead.amountCell(PdfLetterhead.money(advance.getAmount())));
        }
        if (advances.isEmpty()) {
            table.addCell(PdfLetterhead.bodyCell("Aucun règlement enregistré"));
            table.addCell(PdfLetterhead.bodyCell("-"));
            table.addCell(PdfLetterhead.bodyCell("-"));
            table.addCell(PdfLetterhead.amountCell(PdfLetterhead.money(advanceTotal)));
        }
        if (direct.signum() > 0) {
            table.addCell(PdfLetterhead.bodyCell("Versements portés sur les contrats"));
            table.addCell(PdfLetterhead.bodyCell("-"));
            table.addCell(PdfLetterhead.bodyCell("-"));
            table.addCell(PdfLetterhead.amountCell(PdfLetterhead.money(direct)));
        }
        table.addCell(PdfLetterhead.totalCell("Total encaissé", Element.ALIGN_LEFT));
        table.addCell(PdfLetterhead.totalCell("", Element.ALIGN_LEFT));
        table.addCell(PdfLetterhead.totalCell("", Element.ALIGN_LEFT));
        table.addCell(PdfLetterhead.totalCell(PdfLetterhead.money(collected), Element.ALIGN_RIGHT));
        return table;
    }

    @Override
    public byte[] vatSummary(final int year, final int month, final Long projectId) {
        final List<VatByRate> onExpenses = this.expenseRepository.sumVatByRate(projectId, year, month);
        final List<VatByRate> onInvoices = this.supplierInvoiceRepository.sumVatByRate(projectId, year, month);
        final String scope = projectId == null
                ? "Tous les projets"
                : this.projectRepository.findById(projectId)
                        .map(project -> project.getName())
                        .orElse("Projet " + projectId);
        final String period = DocumentServiceImpl.monthLabel(year, month);

        return this.render(document -> {
            PdfLetterhead.open(document, this.company, "Récapitulatif de TVA",
                    year + "-" + String.format("%02d", month));

            document.add(PdfLetterhead.facts(new String[][]{
                    {"Période", period},
                    {"Périmètre", scope}
            }));

            document.add(PdfLetterhead.heading("TVA déductible sur dépenses"));
            document.add(DocumentServiceImpl.vatTable(onExpenses));

            document.add(PdfLetterhead.heading("TVA déductible sur factures fournisseurs"));
            document.add(DocumentServiceImpl.vatTable(onInvoices));

            final BigDecimal total = DocumentServiceImpl.totalVat(onExpenses)
                    .add(DocumentServiceImpl.totalVat(onInvoices));
            document.add(PdfLetterhead.heading("Total déductible du mois"));
            final PdfPTable totals = new PdfPTable(new float[]{3f, 2f});
            totals.setWidthPercentage(100);
            totals.addCell(PdfLetterhead.bodyCell("TVA sur dépenses"));
            totals.addCell(PdfLetterhead.amountCell(
                    PdfLetterhead.money(DocumentServiceImpl.totalVat(onExpenses))));
            totals.addCell(PdfLetterhead.bodyCell("TVA sur factures fournisseurs"));
            totals.addCell(PdfLetterhead.amountCell(
                    PdfLetterhead.money(DocumentServiceImpl.totalVat(onInvoices))));
            totals.addCell(PdfLetterhead.totalCell("Total TVA déductible", Element.ALIGN_LEFT));
            totals.addCell(PdfLetterhead.totalCell(PdfLetterhead.money(total), Element.ALIGN_RIGHT));
            document.add(totals);

            // Sales are recorded gross, so this document cannot claim to be the declaration itself.
            document.add(PdfLetterhead.note(this.messageService.get("document.vat.deductibleOnly")));
        });
    }

    private static PdfPTable vatTable(final List<VatByRate> rows) {
        final PdfPTable table = new PdfPTable(new float[]{1.2f, 2f, 2f});
        table.setWidthPercentage(100);
        table.addCell(PdfLetterhead.headerCell("Taux"));
        table.addCell(PdfLetterhead.headerCell("Base HT"));
        table.addCell(PdfLetterhead.headerCell("TVA"));

        if (rows.isEmpty()) {
            table.addCell(PdfLetterhead.bodyCell("-"));
            table.addCell(PdfLetterhead.amountCell(PdfLetterhead.money(BigDecimal.ZERO)));
            table.addCell(PdfLetterhead.amountCell(PdfLetterhead.money(BigDecimal.ZERO)));
            return table;
        }

        for (final VatByRate row : rows) {
            table.addCell(PdfLetterhead.bodyCell(DocumentServiceImpl.rateLabel(row.rate())));
            table.addCell(PdfLetterhead.amountCell(PdfLetterhead.money(row.baseAmount())));
            table.addCell(PdfLetterhead.amountCell(PdfLetterhead.money(row.vatAmount())));
        }
        table.addCell(PdfLetterhead.totalCell("Total", Element.ALIGN_LEFT));
        table.addCell(PdfLetterhead.totalCell(
                PdfLetterhead.money(DocumentServiceImpl.totalBase(rows)), Element.ALIGN_RIGHT));
        table.addCell(PdfLetterhead.totalCell(
                PdfLetterhead.money(DocumentServiceImpl.totalVat(rows)), Element.ALIGN_RIGHT));
        return table;
    }

    private static BigDecimal totalVat(final List<VatByRate> rows) {
        return rows.stream()
                .map(VatByRate::vatAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal totalBase(final List<VatByRate> rows) {
        return rows.stream()
                .map(VatByRate::baseAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** A stored rate of 0.1900 prints as « 19 % », the way the declaration states it. */
    private static String rateLabel(final BigDecimal rate) {
        if (rate == null) {
            return "Sans TVA";
        }
        final BigDecimal percent = rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros();
        return percent.scale() <= 0
                ? percent.toBigInteger() + " %"
                : percent.setScale(2, RoundingMode.HALF_UP).toPlainString() + " %";
    }

    private static String monthLabel(final int year, final int month) {
        final String name = Month.of(month).getDisplayName(TextStyle.FULL, Locale.FRENCH);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1) + " " + year;
    }

    private static String orDash(final String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String paymentMethodLabel(final ClientAdvance advance) {
        return advance.getPaymentMethod() == null
                ? "-"
                : this.messageService.get("paymentMethod." + advance.getPaymentMethod().name());
    }

    /** Opens an A4 page, hands it to the caller, and closes it into a byte array. */
    private byte[] render(final DocumentBody body) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            final Document document = new Document(PageSize.A4, MARGIN, MARGIN, MARGIN, MARGIN);
            PdfWriter.getInstance(document, output);
            document.open();
            body.write(document);
            document.close();
            return output.toByteArray();
        } catch (final Exception ex) {
            throw new IllegalStateException("Impossible de générer le document PDF", ex);
        }
    }

    /** What a document writes onto an already-open page. */
    @FunctionalInterface
    private interface DocumentBody {
        void write(Document document) throws Exception;
    }
}
