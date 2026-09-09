package com.promoteur.app.persistence;

import com.promoteur.app.invoice.SupplierInvoice;
import com.promoteur.app.invoice.SupplierInvoiceRepository;
import com.promoteur.app.invoice.SupplierInvoiceSpecifications;
import com.promoteur.app.project.Project;
import com.promoteur.app.shared.ListFilter;
import com.promoteur.app.supplier.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the filtering of supplier invoices (PERF-02).
 *
 * <p>The settlement state — paid, partially paid, overdue — is derived from the payments and
 * applied by the service on top of this specification (UX-04), so what belongs here is the
 * column filtering: project, supplier, invoice dates and free text.</p>
 */
class SupplierInvoiceSpecificationTest extends AbstractPersistenceTest {

    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 50);

    @Autowired
    private SupplierInvoiceRepository supplierInvoiceRepository;

    private Project residence;
    private Supplier carrelages;

    @BeforeEach
    void seed() {
        this.residence = this.persistProject("INV-SPEC-1", "Résidence Ghomrassen");
        final Project other = this.persistProject("INV-SPEC-2", "Résidence Tataouine");
        this.carrelages = this.persistSupplier("Carrelages du Sud");
        final Supplier ciment = this.persistSupplier("Ciment de Gabès");

        this.persistInvoice("INV-1", LocalDate.of(2026, 9, 1), this.residence, this.carrelages,
                "Faïence salle de bain");
        this.persistInvoice("INV-2", LocalDate.of(2026, 9, 30), this.residence, ciment, null);
        this.persistInvoice("INV-3", LocalDate.of(2026, 10, 15), other, this.carrelages, null);

        this.settleFixture();
    }

    @Test
    @DisplayName("an empty filter restricts nothing")
    void anEmptyFilterRestrictsNothing() {
        assertThat(this.find(ListFilter.none()).getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("a supplier filter keeps only that supplier's invoices")
    void aSupplierFilterKeepsOnlyThatSuppliersInvoices() {
        final ListFilter bySupplier = new ListFilter(null, null, this.carrelages.getId(), null,
                null, null, null, null, null, null);

        assertThat(this.find(bySupplier).getContent())
                .extracting(SupplierInvoice::getInvoiceNumber)
                .containsExactlyInAnyOrder("INV-1", "INV-3");
    }

    @Test
    @DisplayName("a project filter keeps only that project's invoices")
    void aProjectFilterKeepsOnlyThatProjectsInvoices() {
        final ListFilter byProject = new ListFilter(this.residence.getId(), null, null, null,
                null, null, null, null, null, null);

        assertThat(this.find(byProject).getContent())
                .extracting(SupplierInvoice::getInvoiceNumber)
                .containsExactlyInAnyOrder("INV-1", "INV-2");
    }

    @Test
    @DisplayName("dateFrom and dateTo are inclusive on both ends")
    void dateFromAndDateToAreInclusiveOnBothEnds() {
        final ListFilter september = new ListFilter(null, null, null, null, null, null, null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null);

        assertThat(this.find(september).getContent())
                .extracting(SupplierInvoice::getInvoiceNumber)
                .containsExactlyInAnyOrder("INV-1", "INV-2");
    }

    @Test
    @DisplayName("a search term matches the supplier name through the join")
    void aSearchTermMatchesTheSupplierNameThroughTheJoin() {
        final ListFilter search = new ListFilter(null, null, null, null, null, null, null, null,
                null, "gabès");

        assertThat(this.find(search).getContent())
                .extracting(SupplierInvoice::getInvoiceNumber)
                .containsExactly("INV-2");
    }

    @Test
    @DisplayName("a supplier filter and a project filter narrow together")
    void aSupplierFilterAndAProjectFilterNarrowTogether() {
        final ListFilter both = new ListFilter(this.residence.getId(), null,
                this.carrelages.getId(), null, null, null, null, null, null, null);

        assertThat(this.find(both).getContent())
                .extracting(SupplierInvoice::getInvoiceNumber)
                .containsExactly("INV-1");
    }

    @Test
    @DisplayName("totalElements matches the number of rows a combined filter really selects")
    void totalElementsMatchesTheRowsACombinedFilterSelects() {
        final ListFilter both = new ListFilter(this.residence.getId(), null,
                this.carrelages.getId(), null, null, null, null, null, null, "faïence");

        final Page<SupplierInvoice> page = this.find(both);

        assertThat(page.getTotalElements()).isEqualTo(page.getContent().size());
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("an unfiltered query joins no association at all")
    void anUnfilteredQueryJoinsNoAssociationAtAll() {
        assertThat(this.joinsOf(SupplierInvoice.class,
                SupplierInvoiceSpecifications.matching(ListFilter.none()))).isEmpty();
    }

    @Test
    @DisplayName("a filter joins each association it needs at most once")
    void aFilterJoinsEachAssociationAtMostOnce() {
        final ListFilter everything = new ListFilter(this.residence.getId(), null,
                this.carrelages.getId(), null, null, null, null, null, null, "faïence");

        assertThat(this.joinsOf(SupplierInvoice.class,
                SupplierInvoiceSpecifications.matching(everything)))
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrder("project", "supplier");
    }

    private Page<SupplierInvoice> find(final ListFilter filter) {
        return this.supplierInvoiceRepository.findAll(
                SupplierInvoiceSpecifications.matching(filter), FIRST_PAGE);
    }

    private void persistInvoice(final String number, final LocalDate date, final Project project,
                                final Supplier supplier, final String detail) {
        final SupplierInvoice invoice = new SupplierInvoice();
        invoice.setInvoiceNumber(number);
        invoice.setInvoiceDate(date);
        invoice.setAmountHt(new BigDecimal("1000.000"));
        invoice.setVatRate(new BigDecimal("0.1900"));
        invoice.setVatAmount(new BigDecimal("190.000"));
        invoice.setAmountTtc(new BigDecimal("1190.000"));
        invoice.setProject(project);
        invoice.setSupplier(supplier);
        invoice.setDetail(detail);
        this.entityManager.persist(invoice);
    }
}
