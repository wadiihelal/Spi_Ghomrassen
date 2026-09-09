package com.promoteur.app.persistence;

import com.promoteur.app.expense.Expense;
import com.promoteur.app.expense.ExpenseCategory;
import com.promoteur.app.expense.ExpenseRepository;
import com.promoteur.app.expense.ExpenseSpecifications;
import com.promoteur.app.project.Project;
import com.promoteur.app.shared.ListFilter;
import com.promoteur.app.shared.PaymentMethod;
import com.promoteur.app.supplier.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the server-side filtering of {@code GET /api/expenses} at the level where it is written
 * (PERF-02).
 *
 * <p>{@code ServerSideFilteringTest} checks the same filters through the service, so it sees the
 * rows but not the query. Two kinds of defect hide in that gap: a filter that restricts nothing
 * because a null check is inverted — invisible when the fixture happens to match anyway — and a
 * surplus join, which changes no row at all and can only be seen in the statement itself.</p>
 */
class ExpenseSpecificationTest extends AbstractPersistenceTest {

    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 50);

    @Autowired
    private ExpenseRepository expenseRepository;

    private Project residence;
    private Project other;
    private ExpenseCategory baladiya;
    private ExpenseCategory notaire;
    private Supplier carrelages;

    @BeforeEach
    void seed() {
        this.residence = this.persistProject("SPEC-1", "Résidence Ghomrassen");
        this.other = this.persistProject("SPEC-2", "Résidence Tataouine");
        this.baladiya = this.persistCategory("Frais Baladiya");
        this.notaire = this.persistCategory("Frais Notaire");
        this.carrelages = this.persistSupplier("Carrelages du Sud");

        this.persistExpense("SPEC-DEP-1", "Taxe de bâtisse", LocalDate.of(2026, 9, 1),
                this.residence, this.baladiya, this.carrelages, PaymentMethod.CHECK);
        this.persistExpense("SPEC-DEP-2", "Acte de vente", LocalDate.of(2026, 9, 15),
                this.residence, this.notaire, null, PaymentMethod.CASH);
        this.persistExpense("SPEC-DEP-3", "Taxe de bâtisse", LocalDate.of(2026, 10, 1),
                this.other, this.baladiya, null, PaymentMethod.CHECK);

        this.settleFixture();
    }

    @Test
    @DisplayName("an empty filter restricts nothing")
    void anEmptyFilterRestrictsNothing() {
        assertThat(this.find(ListFilter.none()).getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("a blank search term restricts nothing")
    void aBlankSearchTermRestrictsNothing() {
        for (final String blank : new String[]{"", "   "}) {
            assertThat(this.find(this.filterWithSearch(blank)).getTotalElements())
                    .as("search=%s", blank.isEmpty() ? "(empty)" : "(spaces)")
                    .isEqualTo(3);
        }
    }

    @Test
    @DisplayName("a blank payment method restricts nothing")
    void aBlankPaymentMethodRestrictsNothing() {
        final ListFilter blank = new ListFilter(null, null, null, null, null, null, "   ",
                null, null, null);

        assertThat(this.find(blank).getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("an unfiltered query joins no association at all")
    void anUnfilteredQueryJoinsNoAssociationAtAll() {
        // The search paths are built inside the List.of(...) handed to whenSearch, and Java
        // evaluates arguments before the call: the joins are created even when there is no
        // search term for them to serve.
        assertThat(this.joinsOfExpenses(ListFilter.none())).isEmpty();
    }

    @Test
    @DisplayName("combining a project filter and a search term joins the project once")
    void combiningAProjectFilterAndASearchTermJoinsTheProjectOnce() {
        final ListFilter both = new ListFilter(this.residence.getId(), null, null, null, null,
                null, null, null, null, "bâtisse");

        // whenId joins "project" to compare its id and whenSearch needs it again to read its
        // name; every call to root.join used to add one more. Category and supplier are joined
        // too, legitimately: the search reads their names as well.
        assertThat(this.joinsOfExpenses(both))
                .containsOnlyOnce("project")
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("a filter joins each association it needs at most once")
    void aFilterJoinsEachAssociationAtMostOnce() {
        final ListFilter everything = new ListFilter(this.residence.getId(), null,
                this.carrelages.getId(), this.baladiya.getId(), null, null, "CHECK",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "bâtisse");

        assertThat(this.joinsOfExpenses(everything))
                .containsExactlyInAnyOrder("project", "category", "supplier")
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("totalElements matches the number of rows the filter really selects")
    void totalElementsMatchesTheRowsTheFilterSelects() {
        final ListFilter both = new ListFilter(this.residence.getId(), null, null, null, null,
                null, null, null, null, "bâtisse");

        final Page<Expense> page = this.find(both);

        // Passes today: every joined association is a @ManyToOne, so a duplicated LEFT JOIN
        // cannot multiply rows. Kept as the guard that the join fix does not change the counts.
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getReference()).isEqualTo("SPEC-DEP-1");
    }

    @Test
    @DisplayName("dateFrom and dateTo are inclusive on both ends")
    void dateFromAndDateToAreInclusiveOnBothEnds() {
        final ListFilter september = new ListFilter(null, null, null, null, null, null, null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15), null);

        assertThat(this.find(september).getContent())
                .extracting(Expense::getReference)
                .containsExactlyInAnyOrder("SPEC-DEP-1", "SPEC-DEP-2");
    }

    @Test
    @DisplayName("a search term matches the supplier name through the join")
    void aSearchTermMatchesTheSupplierNameThroughTheJoin() {
        assertThat(this.find(this.filterWithSearch("carrelages")).getContent())
                .extracting(Expense::getReference)
                .containsExactly("SPEC-DEP-1");
    }

    @Test
    @DisplayName("a search term matches the category name through the join")
    void aSearchTermMatchesTheCategoryNameThroughTheJoin() {
        assertThat(this.find(this.filterWithSearch("notaire")).getContent())
                .extracting(Expense::getReference)
                .containsExactly("SPEC-DEP-2");
    }

    @Test
    @DisplayName("a project filter and a category filter narrow together, not separately")
    void aProjectFilterAndACategoryFilterNarrowTogether() {
        final ListFilter both = new ListFilter(this.residence.getId(), null, null,
                this.notaire.getId(), null, null, null, null, null, null);

        assertThat(this.find(both).getContent())
                .extracting(Expense::getReference)
                .containsExactly("SPEC-DEP-2");
    }

    @Test
    @DisplayName("a search term containing a percent sign is treated as a wildcard")
    void aSearchTermContainingAPercentSignIsTreatedAsAWildcard() {
        // Documented behaviour, not a wish: normalizedSearch() wraps the term in % without
        // escaping the % and _ a user may have typed. "b%tisse" therefore matches "bâtisse".
        // Harmless for a search box, and worth knowing before this term is ever reused
        // somewhere a wildcard would matter.
        assertThat(this.find(this.filterWithSearch("b%tisse")).getContent())
                .extracting(Expense::getReference)
                .containsExactlyInAnyOrder("SPEC-DEP-1", "SPEC-DEP-3");
    }


    private List<String> joinsOfExpenses(final ListFilter filter) {
        return this.joinsOf(Expense.class, ExpenseSpecifications.matching(filter));
    }

    private Page<Expense> find(final ListFilter filter) {
        return this.expenseRepository.findAll(ExpenseSpecifications.matching(filter), FIRST_PAGE);
    }

    private ListFilter filterWithSearch(final String search) {
        return new ListFilter(null, null, null, null, null, null, null, null, null, search);
    }


    private ExpenseCategory persistCategory(final String name) {
        final ExpenseCategory category = new ExpenseCategory();
        category.setName(name);
        this.entityManager.persist(category);
        return category;
    }


    private void persistExpense(final String reference, final String description,
                                final LocalDate date, final Project project,
                                final ExpenseCategory category, final Supplier supplier,
                                final PaymentMethod paymentMethod) {
        final Expense expense = new Expense();
        expense.setReference(reference);
        expense.setDescription(description);
        expense.setExpenseDate(date);
        expense.setAmountHt(new BigDecimal("1000.000"));
        expense.setVatRate(new BigDecimal("0.0700"));
        expense.setVatAmount(new BigDecimal("70.000"));
        expense.setAmountTtc(new BigDecimal("1070.000"));
        expense.setPaymentMethod(paymentMethod);
        expense.setProject(project);
        expense.setCategory(category);
        expense.setSupplier(supplier);
        this.entityManager.persist(expense);
    }
}
