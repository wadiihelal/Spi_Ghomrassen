package com.promoteur.app.persistence;

import com.promoteur.app.advance.ClientAdvance;
import com.promoteur.app.apartment.Apartment;
import com.promoteur.app.client.Client;
import com.promoteur.app.project.Project;
import com.promoteur.app.purchase.ClientPurchase;
import com.promoteur.app.purchase.ClientPurchaseRepository;
import com.promoteur.app.purchase.ClientPurchaseSpecifications;
import com.promoteur.app.shared.ListFilter;
import com.promoteur.app.shared.PaymentMethod;
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
 * Covers the filtering of sale contracts, and above all the one filter that is not a column
 * (PERF-02).
 *
 * <p>{@code paymentStatus} is derived: it compares the contract total against what has been
 * collected, which is the direct payment plus every advance recorded on the apartment. Nothing
 * stores it, so the filter is a correlated subquery — and a subquery is where an off-by-one
 * boundary hides. A contract collected to exactly its total is PAID, not PARTIALLY_PAID; one
 * collected to zero is UNPAID, not PARTIALLY_PAID.</p>
 */
class ClientPurchaseSpecificationTest extends AbstractPersistenceTest {

    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 50);
    private static final BigDecimal CONTRACT_TOTAL = new BigDecimal("100000.000");

    @Autowired
    private ClientPurchaseRepository clientPurchaseRepository;

    private Project residence;
    private Project other;
    private Client acquirer;

    @BeforeEach
    void seed() {
        this.residence = this.persistProject("PUR-SPEC-1", "Résidence Ghomrassen");
        this.other = this.persistProject("PUR-SPEC-2", "Résidence Tataouine");
        this.acquirer = this.persistClient(this.residence, "Béchir Ben Salah");

        // apartment_id is NOT NULL and unique on client_purchases: one lot per contract.
        // Nothing collected at all.
        this.persistPurchase("PUR-UNPAID", this.residence, BigDecimal.ZERO,
                this.persistApartment(this.residence, "P-1", CONTRACT_TOTAL));
        // Half collected, as a direct payment.
        this.persistPurchase("PUR-PARTIAL", this.residence, new BigDecimal("50000.000"),
                this.persistApartment(this.residence, "P-2", CONTRACT_TOTAL));
        // Collected to exactly the total, half directly and half through an advance.
        final Apartment paidApartment = this.persistApartment(this.residence, "P-3", CONTRACT_TOTAL);
        this.persistPurchase("PUR-PAID", this.residence, new BigDecimal("50000.000"), paidApartment);
        this.persistAdvance("ACC-PAID", paidApartment, new BigDecimal("50000.000"));
        // Another project, to check the scope filter.
        this.persistPurchase("PUR-OTHER", this.other, BigDecimal.ZERO,
                this.persistApartment(this.other, "P-4", CONTRACT_TOTAL));

        this.settleFixture();
    }

    @Test
    @DisplayName("an empty filter restricts nothing")
    void anEmptyFilterRestrictsNothing() {
        assertThat(this.find(ListFilter.none()).getTotalElements()).isEqualTo(4);
    }

    @Test
    @DisplayName("a project filter keeps only that project's contracts")
    void aProjectFilterKeepsOnlyThatProjectsContracts() {
        assertThat(this.find(this.byProject()).getContent())
                .extracting(ClientPurchase::getReference)
                .containsExactlyInAnyOrder("PUR-UNPAID", "PUR-PARTIAL", "PUR-PAID");
    }

    @Test
    @DisplayName("a contract with nothing collected is unpaid, not partially paid")
    void aContractWithNothingCollectedIsUnpaid() {
        assertThat(this.findByStatus("UNPAID"))
                .extracting(ClientPurchase::getReference)
                .containsExactlyInAnyOrder("PUR-UNPAID", "PUR-OTHER");
    }

    @Test
    @DisplayName("a contract collected in part is partially paid")
    void aContractCollectedInPartIsPartiallyPaid() {
        assertThat(this.findByStatus("PARTIALLY_PAID"))
                .extracting(ClientPurchase::getReference)
                .containsExactly("PUR-PARTIAL");
    }

    @Test
    @DisplayName("a contract collected to exactly its total is paid, advances included")
    void aContractCollectedToExactlyItsTotalIsPaid() {
        // The boundary that matters: 50 000 paid directly plus a 50 000 advance reaches the
        // 100 000 total, so the contract is PAID and not PARTIALLY_PAID.
        assertThat(this.findByStatus("PAID"))
                .extracting(ClientPurchase::getReference)
                .containsExactly("PUR-PAID");
    }

    @Test
    @DisplayName("the three derived statuses partition the contracts, none counted twice")
    void theThreeDerivedStatusesPartitionTheContracts() {
        final long unpaid = this.findByStatus("UNPAID").size();
        final long partial = this.findByStatus("PARTIALLY_PAID").size();
        final long paid = this.findByStatus("PAID").size();

        assertThat(unpaid + partial + paid)
                .as("every contract falls in exactly one derived status")
                .isEqualTo(4);
    }

    @Test
    @DisplayName("a status filter and a project filter narrow together")
    void aStatusFilterAndAProjectFilterNarrowTogether() {
        final ListFilter both = new ListFilter(this.residence.getId(), null, null, null, null,
                "UNPAID", null, null, null, null);

        assertThat(this.find(both).getContent())
                .extracting(ClientPurchase::getReference)
                .containsExactly("PUR-UNPAID");
    }

    @Test
    @DisplayName("totalElements matches the number of rows a combined filter really selects")
    void totalElementsMatchesTheRowsACombinedFilterSelects() {
        final ListFilter both = new ListFilter(this.residence.getId(), this.acquirer.getId(),
                null, null, null, null, null, null, null, "béchir");

        final Page<ClientPurchase> page = this.find(both);

        assertThat(page.getTotalElements()).isEqualTo(page.getContent().size());
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("an unfiltered query joins no association at all")
    void anUnfilteredQueryJoinsNoAssociationAtAll() {
        assertThat(this.joinsOf(ClientPurchase.class,
                ClientPurchaseSpecifications.matching(ListFilter.none()))).isEmpty();
    }

    @Test
    @DisplayName("a filter joins each association it needs at most once")
    void aFilterJoinsEachAssociationAtMostOnce() {
        final ListFilter everything = new ListFilter(this.residence.getId(),
                this.acquirer.getId(), null, null, null, null, null, null, null, "béchir");

        assertThat(this.joinsOf(ClientPurchase.class,
                ClientPurchaseSpecifications.matching(everything)))
                .doesNotHaveDuplicates()
                .contains("project", "client");
    }

    private java.util.List<ClientPurchase> findByStatus(final String status) {
        return this.find(new ListFilter(null, null, null, null, null, status, null, null, null,
                null)).getContent();
    }

    private ListFilter byProject() {
        return new ListFilter(this.residence.getId(), null, null, null, null, null, null, null,
                null, null);
    }

    private Page<ClientPurchase> find(final ListFilter filter) {
        return this.clientPurchaseRepository.findAll(
                ClientPurchaseSpecifications.matching(filter), FIRST_PAGE);
    }

    private void persistPurchase(final String reference, final Project project,
                                 final BigDecimal paidAmount, final Apartment apartment) {
        final ClientPurchase purchase = new ClientPurchase();
        purchase.setReference(reference);
        purchase.setPurchaseDate(LocalDate.of(2026, 9, 1));
        purchase.setAssetDescription("Appartement " + reference);
        purchase.setTotalAmount(CONTRACT_TOTAL);
        purchase.setPaidAmount(paidAmount);
        purchase.setClient(this.acquirer);
        purchase.setProject(project);
        purchase.setApartment(apartment);
        this.entityManager.persist(purchase);
    }

    private void persistAdvance(final String reference, final Apartment apartment,
                                final BigDecimal amount) {
        final ClientAdvance advance = new ClientAdvance();
        advance.setReference(reference);
        advance.setAdvanceDate(LocalDate.of(2026, 9, 15));
        advance.setAmount(amount);
        advance.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        advance.setClient(this.acquirer);
        advance.setProject(this.residence);
        advance.setApartment(apartment);
        this.entityManager.persist(advance);
    }
}
