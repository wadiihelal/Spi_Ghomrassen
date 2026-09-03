package com.promoteur.app.repository.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared building blocks for the list-endpoint specifications (PERF-02).
 */
final class SpecificationSupport {

    private SpecificationSupport() {
    }

    /** Adds {@code path.id = value} when the value is present. */
    static void whenId(final List<Predicate> predicates, final CriteriaBuilder builder,
                       final Root<?> root, final String association, final Long value) {
        if (value != null) {
            predicates.add(builder.equal(root.join(association, JoinType.LEFT).get("id"), value));
        }
    }

    /** Adds {@code attribute = value} when the value is a non-blank string. */
    static void whenText(final List<Predicate> predicates, final CriteriaBuilder builder,
                         final Root<?> root, final String attribute, final String value) {
        if (value != null && !value.isBlank()) {
            predicates.add(builder.equal(root.get(attribute).as(String.class), value.trim()));
        }
    }

    /**
     * Adds an OR of case-insensitive LIKE comparisons across the given paths, when a search term
     * is present.
     */
    static void whenSearch(final List<Predicate> predicates, final CriteriaBuilder builder,
                           final String pattern, final List<Path<String>> paths) {
        if (pattern == null) {
            return;
        }
        final List<Predicate> matches = new ArrayList<>(paths.size());
        for (final Path<String> path : paths) {
            matches.add(builder.like(builder.lower(path.as(String.class)), pattern));
        }
        predicates.add(builder.or(matches.toArray(Predicate[]::new)));
    }

    /** Left-joins an association and returns one of its string attributes, safe when absent. */
    static Path<String> joined(final Root<?> root, final String association, final String attribute) {
        return root.join(association, JoinType.LEFT).get(attribute);
    }

    static Predicate all(final CriteriaBuilder builder, final List<Predicate> predicates) {
        return predicates.isEmpty() ? builder.conjunction() : builder.and(predicates.toArray(Predicate[]::new));
    }

    /** Guards against a null expression in an arithmetic comparison. */
    static <T extends Number> Expression<T> orZero(final CriteriaBuilder builder,
                                                   final Expression<T> expression, final T zero) {
        return builder.coalesce(expression, zero);
    }
}
