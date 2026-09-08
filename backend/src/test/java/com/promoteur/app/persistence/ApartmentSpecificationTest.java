package com.promoteur.app.persistence;

import com.promoteur.app.dto.ListFilter;
import com.promoteur.app.entity.Apartment;
import com.promoteur.app.entity.Client;
import com.promoteur.app.entity.Project;
import com.promoteur.app.repository.ApartmentRepository;
import com.promoteur.app.repository.specification.ApartmentSpecifications;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the filtering of the apartment stock (PERF-02).
 *
 * <p>The acquirer is an optional association, which is what makes this specification worth its
 * own test: a lot with no buyer must still appear in an unfiltered list, and a left join is the
 * only reason it does.</p>
 */
class ApartmentSpecificationTest extends AbstractPersistenceTest {

    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 50);

    @Autowired
    private ApartmentRepository apartmentRepository;

    private Project residence;
    private Client acquirer;

    @BeforeEach
    void seed() {
        this.residence = this.persistProject("APT-SPEC-1", "Résidence Ghomrassen");
        final Project other = this.persistProject("APT-SPEC-2", "Résidence Tataouine");
        this.acquirer = this.persistClient(this.residence, "Amira Trabelsi");

        final Apartment sold = this.persistApartment(this.residence, "A-11", new BigDecimal("90000.000"));
        sold.setAcquirer(this.acquirer);
        this.persistApartment(this.residence, "A-12", new BigDecimal("95000.000"));
        this.persistApartment(other, "B-21", new BigDecimal("80000.000"));

        this.settleFixture();
    }

    @Test
    @DisplayName("an empty filter restricts nothing, including a lot with no acquirer")
    void anEmptyFilterRestrictsNothing() {
        assertThat(this.find(ListFilter.none()).getContent())
                .extracting(Apartment::getApartmentNumber)
                .containsExactlyInAnyOrder("A-11", "A-12", "B-21");
    }

    @Test
    @DisplayName("a project filter keeps only that project's lots")
    void aProjectFilterKeepsOnlyThatProjectsLots() {
        final ListFilter byProject = new ListFilter(this.residence.getId(), null, null, null,
                null, null, null, null, null, null);

        assertThat(this.find(byProject).getContent())
                .extracting(Apartment::getApartmentNumber)
                .containsExactlyInAnyOrder("A-11", "A-12");
    }

    @Test
    @DisplayName("a client filter keeps only the lots that client acquired")
    void aClientFilterKeepsOnlyTheLotsThatClientAcquired() {
        final ListFilter byClient = new ListFilter(null, this.acquirer.getId(), null, null, null,
                null, null, null, null, null);

        assertThat(this.find(byClient).getContent())
                .extracting(Apartment::getApartmentNumber)
                .containsExactly("A-11");
    }

    @Test
    @DisplayName("a search term matches the lot number and the acquirer name")
    void aSearchTermMatchesTheLotNumberAndTheAcquirerName() {
        assertThat(this.find(this.search("a-1")).getContent())
                .extracting(Apartment::getApartmentNumber)
                .containsExactlyInAnyOrder("A-11", "A-12");
        assertThat(this.find(this.search("trabelsi")).getContent())
                .extracting(Apartment::getApartmentNumber)
                .containsExactly("A-11");
    }

    @Test
    @DisplayName("a project filter and a search term narrow together")
    void aProjectFilterAndASearchTermNarrowTogether() {
        final ListFilter both = new ListFilter(this.residence.getId(), null, null, null, null,
                null, null, null, null, "a-11");

        assertThat(this.find(both).getContent())
                .extracting(Apartment::getApartmentNumber)
                .containsExactly("A-11");
    }

    @Test
    @DisplayName("totalElements matches the number of rows a combined filter really selects")
    void totalElementsMatchesTheRowsACombinedFilterSelects() {
        final ListFilter both = new ListFilter(this.residence.getId(), null, null, null, null,
                null, null, null, null, "a-1");

        final Page<Apartment> page = this.find(both);

        assertThat(page.getTotalElements()).isEqualTo(page.getContent().size());
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("an unfiltered query joins no association at all")
    void anUnfilteredQueryJoinsNoAssociationAtAll() {
        assertThat(this.joinsOf(Apartment.class,
                ApartmentSpecifications.matching(ListFilter.none()))).isEmpty();
    }

    @Test
    @DisplayName("a filter joins each association it needs at most once")
    void aFilterJoinsEachAssociationAtMostOnce() {
        final ListFilter everything = new ListFilter(this.residence.getId(),
                this.acquirer.getId(), null, null, null, null, null, null, null, "trabelsi");

        assertThat(this.joinsOf(Apartment.class, ApartmentSpecifications.matching(everything)))
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrder("project", "acquirer");
    }

    private ListFilter search(final String term) {
        return new ListFilter(null, null, null, null, null, null, null, null, null, term);
    }

    private Page<Apartment> find(final ListFilter filter) {
        return this.apartmentRepository.findAll(ApartmentSpecifications.matching(filter), FIRST_PAGE);
    }
}
