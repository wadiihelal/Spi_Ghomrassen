package com.promoteur.app.persistence;

import com.promoteur.app.dto.ListFilter;
import com.promoteur.app.entity.Apartment;
import com.promoteur.app.entity.Client;
import com.promoteur.app.entity.ClientAdvance;
import com.promoteur.app.entity.Project;
import com.promoteur.app.enums.PaymentMethod;
import com.promoteur.app.repository.ClientAdvanceRepository;
import com.promoteur.app.repository.specification.ClientAdvanceSpecifications;
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
 * Covers the filtering of collected payments (PERF-02).
 *
 * <p>This is the one specification whose {@code paymentMethod} filter compares an enum stored as
 * a string, so it is also where a blank value must restrict nothing rather than match the empty
 * string.</p>
 */
class ClientAdvanceSpecificationTest extends AbstractPersistenceTest {

    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 50);

    @Autowired
    private ClientAdvanceRepository clientAdvanceRepository;

    private Project residence;
    private Client acquirer;
    private Apartment apartment;

    @BeforeEach
    void seed() {
        this.residence = this.persistProject("ADV-SPEC-1", "Résidence Ghomrassen");
        final Project other = this.persistProject("ADV-SPEC-2", "Résidence Tataouine");
        this.acquirer = this.persistClient(this.residence, "Néjib Ferchichi");
        this.apartment = this.persistApartment(this.residence, "V-31", new BigDecimal("120000.000"));

        this.persistAdvance("ADV-1", LocalDate.of(2026, 9, 1), PaymentMethod.CHECK,
                this.residence, this.apartment, "Premier versement");
        this.persistAdvance("ADV-2", LocalDate.of(2026, 9, 20), PaymentMethod.BANK_TRANSFER,
                this.residence, this.apartment, "Second versement");
        final Apartment elsewhere = this.persistApartment(other, "W-41", new BigDecimal("90000.000"));
        this.persistAdvance("ADV-3", LocalDate.of(2026, 10, 5), PaymentMethod.CASH,
                other, elsewhere, null);

        this.settleFixture();
    }

    @Test
    @DisplayName("an empty filter restricts nothing")
    void anEmptyFilterRestrictsNothing() {
        assertThat(this.find(ListFilter.none()).getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("a blank payment method restricts nothing")
    void aBlankPaymentMethodRestrictsNothing() {
        final ListFilter blank = new ListFilter(null, null, null, null, null, null, "   ", null,
                null, null);

        assertThat(this.find(blank).getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("a payment method filter keeps only the payments settled that way")
    void aPaymentMethodFilterKeepsOnlyThosePayments() {
        final ListFilter byCheck = new ListFilter(null, null, null, null, null, null, "CHECK",
                null, null, null);

        assertThat(this.find(byCheck).getContent())
                .extracting(ClientAdvance::getReference)
                .containsExactly("ADV-1");
    }

    @Test
    @DisplayName("an apartment filter keeps only the payments on that lot")
    void anApartmentFilterKeepsOnlyThePaymentsOnThatLot() {
        final ListFilter byApartment = new ListFilter(null, null, null, null,
                this.apartment.getId(), null, null, null, null, null);

        assertThat(this.find(byApartment).getContent())
                .extracting(ClientAdvance::getReference)
                .containsExactlyInAnyOrder("ADV-1", "ADV-2");
    }

    @Test
    @DisplayName("dateFrom and dateTo are inclusive on both ends")
    void dateFromAndDateToAreInclusiveOnBothEnds() {
        final ListFilter september = new ListFilter(null, null, null, null, null, null, null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20), null);

        assertThat(this.find(september).getContent())
                .extracting(ClientAdvance::getReference)
                .containsExactlyInAnyOrder("ADV-1", "ADV-2");
    }

    @Test
    @DisplayName("a search term matches the lot number through the join")
    void aSearchTermMatchesTheLotNumberThroughTheJoin() {
        final ListFilter search = new ListFilter(null, null, null, null, null, null, null, null,
                null, "v-31");

        assertThat(this.find(search).getContent())
                .extracting(ClientAdvance::getReference)
                .containsExactlyInAnyOrder("ADV-1", "ADV-2");
    }

    @Test
    @DisplayName("totalElements matches the number of rows a combined filter really selects")
    void totalElementsMatchesTheRowsACombinedFilterSelects() {
        final ListFilter both = new ListFilter(this.residence.getId(), this.acquirer.getId(),
                null, null, null, null, "CHECK", null, null, "versement");

        final Page<ClientAdvance> page = this.find(both);

        assertThat(page.getTotalElements()).isEqualTo(page.getContent().size());
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("an unfiltered query joins no association at all")
    void anUnfilteredQueryJoinsNoAssociationAtAll() {
        assertThat(this.joinsOf(ClientAdvance.class,
                ClientAdvanceSpecifications.matching(ListFilter.none()))).isEmpty();
    }

    @Test
    @DisplayName("a filter joins each association it needs at most once")
    void aFilterJoinsEachAssociationAtMostOnce() {
        final ListFilter everything = new ListFilter(this.residence.getId(),
                this.acquirer.getId(), null, null, this.apartment.getId(), null, "CHECK",
                null, null, "versement");

        assertThat(this.joinsOf(ClientAdvance.class,
                ClientAdvanceSpecifications.matching(everything)))
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrder("project", "client", "apartment");
    }

    private Page<ClientAdvance> find(final ListFilter filter) {
        return this.clientAdvanceRepository.findAll(
                ClientAdvanceSpecifications.matching(filter), FIRST_PAGE);
    }

    private void persistAdvance(final String reference, final LocalDate date,
                                final PaymentMethod method, final Project project,
                                final Apartment apartment, final String notes) {
        final ClientAdvance advance = new ClientAdvance();
        advance.setReference(reference);
        advance.setAdvanceDate(date);
        advance.setAmount(new BigDecimal("10000.000"));
        advance.setPaymentMethod(method);
        advance.setClient(this.acquirer);
        advance.setProject(project);
        advance.setApartment(apartment);
        advance.setNotes(notes);
        this.entityManager.persist(advance);
    }
}
