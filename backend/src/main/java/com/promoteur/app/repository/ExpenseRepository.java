package com.promoteur.app.repository;

import com.promoteur.app.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByCategoryId(Long categoryId);

    List<Expense> findByProjectId(Long projectId);

    Page<Expense> findByCategoryId(Long categoryId, Pageable pageable);

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
}
