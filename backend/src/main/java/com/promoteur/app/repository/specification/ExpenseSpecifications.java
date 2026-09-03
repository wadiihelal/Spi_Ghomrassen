package com.promoteur.app.repository.specification;

import com.promoteur.app.dto.ListFilter;
import com.promoteur.app.entity.Expense;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side filtering for {@code GET /api/expenses} (PERF-02).
 */
public final class ExpenseSpecifications {

    private ExpenseSpecifications() {
    }

    public static Specification<Expense> matching(final ListFilter filter) {
        return (root, query, builder) -> {
            final List<Predicate> predicates = new ArrayList<>();

            SpecificationSupport.whenId(predicates, builder, root, "project", filter.projectId());
            SpecificationSupport.whenId(predicates, builder, root, "category", filter.categoryId());
            SpecificationSupport.whenId(predicates, builder, root, "supplier", filter.supplierId());
            SpecificationSupport.whenText(predicates, builder, root, "paymentMethod", filter.paymentMethod());

            if (filter.dateFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("expenseDate"), filter.dateFrom()));
            }
            if (filter.dateTo() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("expenseDate"), filter.dateTo()));
            }

            SpecificationSupport.whenSearch(predicates, builder, filter.normalizedSearch(), List.of(
                    root.get("reference"),
                    root.get("description"),
                    root.get("documentNumber"),
                    root.get("notes"),
                    SpecificationSupport.joined(root, "project", "name"),
                    SpecificationSupport.joined(root, "category", "name"),
                    SpecificationSupport.joined(root, "supplier", "name")));

            return SpecificationSupport.all(builder, predicates);
        };
    }
}
