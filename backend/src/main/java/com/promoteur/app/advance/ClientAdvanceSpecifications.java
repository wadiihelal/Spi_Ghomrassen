package com.promoteur.app.advance;

import com.promoteur.app.shared.ListFilter;
import com.promoteur.app.shared.SpecificationSupport;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side filtering for {@code GET /api/client-advances} (PERF-02).
 */
public final class ClientAdvanceSpecifications {

    private ClientAdvanceSpecifications() {
    }

    public static Specification<ClientAdvance> matching(final ListFilter filter) {
        return (root, query, builder) -> {
            final List<Predicate> predicates = new ArrayList<>();

            SpecificationSupport.whenId(predicates, builder, root, "project", filter.projectId());
            SpecificationSupport.whenId(predicates, builder, root, "client", filter.clientId());
            SpecificationSupport.whenId(predicates, builder, root, "apartment", filter.apartmentId());
            SpecificationSupport.whenText(predicates, builder, root, "paymentMethod", filter.paymentMethod());

            if (filter.dateFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("advanceDate"), filter.dateFrom()));
            }
            if (filter.dateTo() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("advanceDate"), filter.dateTo()));
            }

            SpecificationSupport.whenSearch(predicates, builder, filter.normalizedSearch(), () -> List.of(
                    root.get("reference"),
                    root.get("notes"),
                    SpecificationSupport.joined(root, "project", "name"),
                    SpecificationSupport.joined(root, "client", "fullName"),
                    SpecificationSupport.joined(root, "apartment", "apartmentNumber")));

            return SpecificationSupport.all(builder, predicates);
        };
    }
}
