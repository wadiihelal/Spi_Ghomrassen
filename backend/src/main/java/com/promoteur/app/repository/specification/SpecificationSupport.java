package com.promoteur.app.repository.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

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
            predicates.add(builder.equal(joinOnce(root, association).get("id"), value));
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
     *
     * <p>The paths arrive as a {@link Supplier} rather than a {@code List} on purpose. Building
     * them joins associations, and a {@code List.of(...)} argument is evaluated before this
     * method is even entered: the joins were created on every query, including the unfiltered
     * listing that has no search term to use them for.</p>
     *
     * @param paths supplier of the columns to match, invoked only when there is a term
     */
    static void whenSearch(final List<Predicate> predicates, final CriteriaBuilder builder,
                           final String pattern, final Supplier<List<Path<String>>> paths) {
        if (pattern == null) {
            return;
        }
        final List<Path<String>> resolved = paths.get();
        final List<Predicate> matches = new ArrayList<>(resolved.size());
        for (final Path<String> path : resolved) {
            matches.add(builder.like(builder.lower(path.as(String.class)), pattern));
        }
        predicates.add(builder.or(matches.toArray(Predicate[]::new)));
    }

    /** Left-joins an association and returns one of its string attributes, safe when absent. */
    static Path<String> joined(final Root<?> root, final String association, final String attribute) {
        return joinOnce(root, association).get(attribute);
    }

    /**
     * Reuses the left join already made on an association, or creates it.
     *
     * <p>{@code root.join(...)} adds a join every time it is called, so filtering on
     * {@code projectId} and searching a project name used to join {@code projects} twice — six
     * joins for three associations once every filter was set. Reusing the existing join keeps
     * one per association, whatever the combination of filters.</p>
     */
    private static Join<?, ?> joinOnce(final Root<?> root, final String association) {
        for (final Join<?, ?> existing : root.getJoins()) {
            if (existing.getAttribute().getName().equals(association)
                    && existing.getJoinType() == JoinType.LEFT) {
                return existing;
            }
        }
        return root.join(association, JoinType.LEFT);
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
