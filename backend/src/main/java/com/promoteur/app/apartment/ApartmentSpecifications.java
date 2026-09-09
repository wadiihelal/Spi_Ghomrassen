package com.promoteur.app.apartment;

import com.promoteur.app.shared.ListFilter;
import com.promoteur.app.shared.SpecificationSupport;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side filtering for {@code GET /api/apartments} (PERF-02).
 */
public final class ApartmentSpecifications {

    private ApartmentSpecifications() {
    }

    public static Specification<Apartment> matching(final ListFilter filter) {
        return (root, query, builder) -> {
            final List<Predicate> predicates = new ArrayList<>();

            SpecificationSupport.whenId(predicates, builder, root, "project", filter.projectId());
            SpecificationSupport.whenId(predicates, builder, root, "acquirer", filter.clientId());

            SpecificationSupport.whenSearch(predicates, builder, filter.normalizedSearch(), () -> List.of(
                    root.get("apartmentNumber"),
                    root.get("apartmentType"),
                    root.get("detail"),
                    SpecificationSupport.joined(root, "project", "name"),
                    SpecificationSupport.joined(root, "acquirer", "fullName")));

            return SpecificationSupport.all(builder, predicates);
        };
    }
}
