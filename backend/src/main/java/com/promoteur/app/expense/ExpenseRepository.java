package com.promoteur.app.expense;

import com.promoteur.app.report.AmountByLabelDto;
import com.promoteur.app.report.CountAndTotal;
import com.promoteur.app.report.MonthlyAmount;
import com.promoteur.app.vat.VatByRate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {
    /**
     * Filtered list endpoint (PERF-02). Redeclared from {@link JpaSpecificationExecutor} so the
     * entity graph applies here as well: filtering must not reintroduce the N+1.
     */
    @EntityGraph(attributePaths = {"category", "project", "supplier"})
    @Override
    Page<Expense> findAll(Specification<Expense> specification, Pageable pageable);

    /**
     * One query for the list endpoint: the associations the response needs are joined.
     */
    @EntityGraph(attributePaths = {"category", "project", "supplier"})
    @Override
    Page<Expense> findAll(Pageable pageable);

    List<Expense> findByCategoryId(Long categoryId);

    List<Expense> findByProjectId(Long projectId);

    @EntityGraph(attributePaths = {"category", "project", "supplier"})
    Page<Expense> findByCategoryId(Long categoryId, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "project", "supplier"})
    Page<Expense> findByProjectId(Long projectId, Pageable pageable);

    Optional<Expense> findByReference(String reference);

    /**
     * Expenses inside a report scope. Every parameter is optional: {@code null} does not
     * restrict.
     */
    @Query("""
            select e from Expense e
            where (:projectId is null or e.project.id = :projectId)
              and (:from is null or e.expenseDate >= :from)
              and (:to is null or e.expenseDate <= :to)
            """)
    List<Expense> findForReport(@Param("projectId") Long projectId,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to);

    /**
     * PERF-01: expense totals grouped by category, aggregated and paginated by the database
     * instead of loading every row and grouping in Java.
     */
    @Query(value = """
            select new com.promoteur.app.report.AmountByLabelDto(e.category.name, sum(e.amountTtc))
            from Expense e
            where (:projectId is null or e.project.id = :projectId)
              and (:from is null or e.expenseDate >= :from)
              and (:to is null or e.expenseDate <= :to)
            group by e.category.name
            order by sum(e.amountTtc) desc
            """,
            countQuery = """
                    select count(distinct e.category.name)
                    from Expense e
                    where (:projectId is null or e.project.id = :projectId)
                      and (:from is null or e.expenseDate >= :from)
                      and (:to is null or e.expenseDate <= :to)
                    """)
    Page<AmountByLabelDto> sumByCategory(@Param("projectId") Long projectId,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to,
                                         Pageable pageable);

    /**
     * PERF-01: expense totals grouped by project.
     */
    @Query(value = """
            select new com.promoteur.app.report.AmountByLabelDto(e.project.name, sum(e.amountTtc))
            from Expense e
            where (:projectId is null or e.project.id = :projectId)
              and (:from is null or e.expenseDate >= :from)
              and (:to is null or e.expenseDate <= :to)
            group by e.project.name
            order by sum(e.amountTtc) desc
            """,
            countQuery = """
                    select count(distinct e.project.name)
                    from Expense e
                    where (:projectId is null or e.project.id = :projectId)
                      and (:from is null or e.expenseDate >= :from)
                      and (:to is null or e.expenseDate <= :to)
                    """)
    Page<AmountByLabelDto> sumByProject(@Param("projectId") Long projectId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to,
                                        Pageable pageable);

    /**
     * Expense count and total for the dashboard summary, in one query.
     */
    @Query("""
            select new com.promoteur.app.report.CountAndTotal(count(e), coalesce(sum(e.amountTtc), 0))
            from Expense e
            where (:projectId is null or e.project.id = :projectId)
              and (:from is null or e.expenseDate >= :from)
              and (:to is null or e.expenseDate <= :to)
            """)
    CountAndTotal countAndTotal(@Param("projectId") Long projectId,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to);

    /**
     * Expense totals per calendar month, for the dashboard's trend bars. Grouped by the database
     * so the browser never needs the underlying rows (PERF-02). Year and month are projected as
     * they are grouped: a formatted label in the select list is rejected by PostgreSQL and H2.
     */
    @Query(value = """
            select new com.promoteur.app.report.MonthlyAmount(
                year(e.expenseDate), month(e.expenseDate), sum(e.amountTtc))
            from Expense e
            where (:projectId is null or e.project.id = :projectId)
              and (:from is null or e.expenseDate >= :from)
              and (:to is null or e.expenseDate <= :to)
            group by year(e.expenseDate), month(e.expenseDate)
            order by year(e.expenseDate), month(e.expenseDate)
            """,
            countQuery = """
                    select count(distinct concat(year(e.expenseDate), '-', month(e.expenseDate)))
                    from Expense e
                    where (:projectId is null or e.project.id = :projectId)
                      and (:from is null or e.expenseDate >= :from)
                      and (:to is null or e.expenseDate <= :to)
                    """)
    Page<MonthlyAmount> sumByMonth(@Param("projectId") Long projectId,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to,
                                   Pageable pageable);


    /**
     * Deductible VAT of one month, grouped by rate (UX-06). Aggregated in the database: the
     * declaration must not depend on loading a month of expenses into memory.
     */
    @Query("""
            select new com.promoteur.app.vat.VatByRate(e.vatRate, sum(e.amountHt), sum(e.vatAmount))
            from Expense e
            where (:projectId is null or e.project.id = :projectId)
              and year(e.expenseDate) = :year
              and month(e.expenseDate) = :month
            group by e.vatRate
            order by e.vatRate
            """)
    List<VatByRate> sumVatByRate(@Param("projectId") Long projectId,
                                 @Param("year") int year,
                                 @Param("month") int month);
}
