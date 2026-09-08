package com.promoteur.app.repository.specification;

import com.promoteur.app.dto.ListFilter;
import com.promoteur.app.entity.SupplierInvoice;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side filtering for {@code GET /api/supplier-invoices} (PERF-02).
 */
public final class SupplierInvoiceSpecifications {

    private SupplierInvoiceSpecifications() {
    }

    public static Specification<SupplierInvoice> matching(final ListFilter filter) {
        return (root, query, builder) -> {
            final List<Predicate> predicates = new ArrayList<>();

            SpecificationSupport.whenId(predicates, builder, root, "project", filter.projectId());
            SpecificationSupport.whenId(predicates, builder, root, "supplier", filter.supplierId());

            if (filter.dateFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("invoiceDate"), filter.dateFrom()));
            }
            if (filter.dateTo() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("invoiceDate"), filter.dateTo()));
            }

            SpecificationSupport.whenSearch(predicates, builder, filter.normalizedSearch(), () -> List.of(
                    root.get("invoiceNumber"),
                    root.get("detail"),
                    SpecificationSupport.joined(root, "project", "name"),
                    SpecificationSupport.joined(root, "supplier", "name")));

            return SpecificationSupport.all(builder, predicates);
        };
    }
}
