package com.promoteur.app.persistence;

import com.promoteur.app.expense.Expense;
import com.promoteur.app.expense.ExpenseCategory;
import com.promoteur.app.expense.ExpenseRepository;
import com.promoteur.app.expense.ExpenseSpecifications;
import com.promoteur.app.project.Project;
import com.promoteur.app.shared.ListFilter;
import com.promoteur.app.shared.PaymentMethod;
import com.promoteur.app.supplier.Supplier;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the fetch graph of the expense list at repository level (PERF-01, PERF-03).
 *
 * <p>{@code QueryCountTest} counts queries through the services, which proves the endpoint is
 * free of N+1 today. What it cannot say is <em>why</em>: the reason is the {@code @EntityGraph}
 * redeclared on {@code findAll(Specification, Pageable)} in {@code ExpenseRepository}. Drop that
 * annotation and the service test still passes for the paged case while every row silently
 * fetches three associations of its own. This class pins the annotation itself.</p>
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ExpenseFetchGraphTest extends AbstractPersistenceTest {

    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 50);
    private static final int ROWS = 5;

    @Autowired
    private ExpenseRepository expenseRepository;

    @BeforeEach
    void seed() {
        final Project residence = this.persistProject("GRAPH-1", "Résidence Ghomrassen");
        final ExpenseCategory category = this.persistCategory("Frais Baladiya");
        final Supplier supplier = this.persistSupplier("Carrelages du Sud");

        for (int index = 1; index <= ROWS; index++) {
            final Expense expense = new Expense();
            expense.setReference("GRAPH-DEP-" + index);
            expense.setDescription("Dépense " + index);
            expense.setExpenseDate(LocalDate.of(2026, 9, index));
            expense.setAmountHt(new BigDecimal("1000.000"));
            expense.setVatRate(new BigDecimal("0.0700"));
            expense.setVatAmount(new BigDecimal("70.000"));
            expense.setAmountTtc(new BigDecimal("1070.000"));
            expense.setPaymentMethod(PaymentMethod.CHECK);
            expense.setProject(residence);
            expense.setCategory(category);
            expense.setSupplier(supplier);
            this.entityManager.persist(expense);
        }

        this.settleFixture();
    }

    @Test
    @DisplayName("listing expenses fetches project, category and supplier in one query")
    void listingExpensesFetchesEveryAssociationInOneQuery() {
        final Statistics statistics = this.resetStatistics();

        final Page<Expense> page = this.expenseRepository.findAll(
                ExpenseSpecifications.matching(ListFilter.none()), FIRST_PAGE);
        assertThat(page.getContent()).hasSize(ROWS);

        // One statement, not one plus three per row. Without the entity graph these five rows
        // would cost sixteen — the N+1 signature PERF-01 removed. Spring Data also elides the
        // count query here, since the first page holds every row.
        assertThat(statistics.getPrepareStatementCount())
                .as("one query for five rows and their three associations")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("paging beyond one page adds the count query and nothing else")
    void pagingBeyondOnePageAddsTheCountQueryAndNothingElse() {
        final Statistics statistics = this.resetStatistics();

        final Page<Expense> page = this.expenseRepository.findAll(
                ExpenseSpecifications.matching(ListFilter.none()), PageRequest.of(0, 2));
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(ROWS);

        // Two statements: the rows and the count Spring Data now really needs. Still nothing
        // per row.
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("the associations a row exposes are loaded before the session is cleared")
    void theAssociationsARowExposesAreLoadedEagerly() {
        final Page<Expense> page = this.expenseRepository.findAll(
                ExpenseSpecifications.matching(ListFilter.none()), FIRST_PAGE);

        this.entityManager.clear();

        // Read after clear(): only an association fetched by the entity graph survives. A lazy
        // proxy would raise LazyInitializationException here, which is what a mapper hitting an
        // unloaded association does in production, outside the transaction.
        assertThat(page.getContent()).allSatisfy(expense -> {
            assertThat(Hibernate.isInitialized(expense.getProject())).isTrue();
            assertThat(Hibernate.isInitialized(expense.getCategory())).isTrue();
            assertThat(Hibernate.isInitialized(expense.getSupplier())).isTrue();
            assertThat(expense.getProject().getName()).isEqualTo("Résidence Ghomrassen");
            assertThat(expense.getCategory().getName()).isEqualTo("Frais Baladiya");
            assertThat(expense.getSupplier().getName()).isEqualTo("Carrelages du Sud");
        });
    }

    @Test
    @DisplayName("a filtered listing costs the same number of queries as an unfiltered one")
    void aFilteredListingCostsTheSameNumberOfQueries() {
        final ListFilter filtered = new ListFilter(null, null, null, null, null, null, "CHECK",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "dépense");

        final Statistics statistics = this.resetStatistics();
        this.expenseRepository.findAll(ExpenseSpecifications.matching(filtered), FIRST_PAGE);

        // Filtering must not reintroduce the N+1: the entity graph is redeclared on the
        // specification overload precisely so it applies here too.
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    private ExpenseCategory persistCategory(final String name) {
        final ExpenseCategory category = new ExpenseCategory();
        category.setName(name);
        this.entityManager.persist(category);
        return category;
    }

    private Statistics resetStatistics() {
        final Statistics statistics = this.entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        return statistics;
    }
}
