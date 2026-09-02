package com.promoteur.app.repository;

import com.promoteur.app.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByCategoryId(Long categoryId);

    List<Expense> findByProjectId(Long projectId);

    Page<Expense> findByCategoryId(Long categoryId, Pageable pageable);

    Page<Expense> findByProjectId(Long projectId, Pageable pageable);

    Optional<Expense> findByReference(String reference);
}
