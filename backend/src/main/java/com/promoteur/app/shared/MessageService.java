package com.promoteur.app.shared;

/**
 * Resolves user-facing French labels from {@code messages_fr.properties}.
 *
 * <p>Message keys stay in English, values are accented French. Nothing in the code base should
 * build a user-visible sentence by string concatenation.</p>
 */
public interface MessageService {

    /**
     * Returns the French label for the given key.
     *
     * @param key  message key, e.g. {@code audit.expense.created}
     * @param args positional arguments substituted into the pattern
     * @return the formatted French label
     */
    String get(String key, Object... args);
}
