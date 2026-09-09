package com.promoteur.app.search;


import java.util.List;

/**
 * Finds a record from a few typed characters (UX-08).
 *
 * <p>Clients, lots, sale contracts and supplier invoices are looked up within the project in
 * scope; suppliers are shared, so they are looked up across the company.</p>
 */
public interface SearchService {

    /**
     * Hits are worth returning from this many characters on; below, the list is empty.
     */
    int MIN_QUERY_LENGTH = 2;

    /**
     * Results per type, so the panel stays scannable.
     */
    int HITS_PER_TYPE = 5;

    /**
     * @param query     what the user typed, trimmed and matched case-insensitively
     * @param projectId project to search within for project-scoped records, or null for all
     * @return hits grouped by type in a fixed order, at most {@link #HITS_PER_TYPE} each
     */
    List<SearchHitResponse> search(String query, Long projectId);
}
