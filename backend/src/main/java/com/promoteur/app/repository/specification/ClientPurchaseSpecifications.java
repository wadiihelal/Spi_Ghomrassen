package com.promoteur.app.repository.specification;

import com.promoteur.app.dto.ListFilter;
import com.promoteur.app.entity.ClientAdvance;
import com.promoteur.app.entity.ClientPurchase;
import com.promoteur.app.enums.PurchasePaymentStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-side filtering for {@code GET /api/client-purchases} (PERF-02).
 */
public final class ClientPurchaseSpecifications {

    private ClientPurchaseSpecifications() {
    }

    public static Specification<ClientPurchase> matching(final ListFilter filter) {
        return (root, query, builder) -> {
            final List<Predicate> predicates = new ArrayList<>();

            SpecificationSupport.whenId(predicates, builder, root, "project", filter.projectId());
            SpecificationSupport.whenId(predicates, builder, root, "client", filter.clientId());
            SpecificationSupport.whenId(predicates, builder, root, "apartment", filter.apartmentId());

            if (filter.dateFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("purchaseDate"), filter.dateFrom()));
            }
            if (filter.dateTo() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("purchaseDate"), filter.dateTo()));
            }

            final PurchasePaymentStatus status = filter.purchasePaymentStatus();
            if (status != null) {
                predicates.add(paymentStatusPredicate(root, query, builder, status));
            }

            SpecificationSupport.whenSearch(predicates, builder, filter.normalizedSearch(), List.of(
                    root.get("reference"),
                    root.get("assetDescription"),
                    root.get("notes"),
                    SpecificationSupport.joined(root, "project", "name"),
                    SpecificationSupport.joined(root, "client", "fullName"),
                    SpecificationSupport.joined(root, "apartment", "apartmentNumber")));

            return SpecificationSupport.all(builder, predicates);
        };
    }

    /**
     * The payment status is derived, not stored: it compares the contract total against what has
     * been collected, which is the direct payment plus every advance on the apartment. Resolved
     * here as a correlated subquery so the filter can run in SQL and the page stay server-side.
     */
    private static Predicate paymentStatusPredicate(final Root<ClientPurchase> root,
                                                    final CriteriaQuery<?> query,
                                                    final CriteriaBuilder builder,
                                                    final PurchasePaymentStatus status) {
        final Subquery<BigDecimal> advances = query.subquery(BigDecimal.class);
        final Root<ClientAdvance> advance = advances.from(ClientAdvance.class);
        advances.select(builder.coalesce(builder.sum(advance.get("amount")), BigDecimal.ZERO));
        advances.where(builder.equal(advance.get("apartment"), root.get("apartment")));

        final Expression<BigDecimal> collected = builder.sum(
                SpecificationSupport.orZero(builder, root.get("paidAmount"), BigDecimal.ZERO),
                advances.getSelection().as(BigDecimal.class));
        final Expression<BigDecimal> total =
                SpecificationSupport.orZero(builder, root.get("totalAmount"), BigDecimal.ZERO);

        return switch (status) {
            case UNPAID -> builder.lessThanOrEqualTo(collected, BigDecimal.ZERO);
            case PAID -> builder.greaterThanOrEqualTo(collected, total);
            case PARTIALLY_PAID -> builder.and(
                    builder.greaterThan(collected, BigDecimal.ZERO),
                    builder.lessThan(collected, total));
        };
    }
}
