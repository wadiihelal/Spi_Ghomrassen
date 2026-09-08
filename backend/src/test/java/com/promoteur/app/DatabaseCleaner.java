package com.promoteur.app;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Brings the shared test database back to its start-up state, so that test classes sharing one
 * Spring context do not see each other's rows.
 *
 * <p>Sharing a single H2 database is what makes one context enough for the whole suite. It also
 * means the isolation the old per-class URLs provided has to be recreated explicitly: several
 * classes assert absolute counts — {@code ServerSideFilteringTest} expects exactly three
 * expenses for an unfiltered filter — and would read another class's data without this.</p>
 *
 * <p>Truncation rather than {@code @Transactional} rollback: {@code AdvanceCeilingTest} needs
 * real commits to observe a pessimistic lock across two threads, and {@code QueryCountTest}
 * counts statements that a surrounding transaction would distort.</p>
 *
 * <h2>Why reference tables are preserved</h2>
 *
 * <p>{@code ReferenceDataInitializer} is a {@code CommandLineRunner}: it seeds expense
 * categories, supplier types and VAT rates <b>once</b>, when the context starts. Truncating
 * those tables would leave every later class without them — six classes resolve a category
 * through {@code expenseCategoryRepository.findAll().get(0)}, and {@code VatCalculationTest}
 * asserts the exact four seeded rates. They are therefore excluded from the truncation instead
 * of being re-seeded, which keeps this class independent of {@code config/}.</p>
 */
public class DatabaseCleaner {

    /**
     * Tables left untouched: Flyway's own history, plus the three reference tables seeded at
     * start-up. Named rather than discovered, because "is this reference data?" is a business
     * question that no JDBC metadata can answer.
     */
    private static final Set<String> PRESERVED_TABLES = Set.of(
            "flyway_schema_history",
            "expense_categories",
            "supplier_type_options",
            "vat_rate_options");

    /** Document reference counters (V6 and V12), reset so each class numbers from one. */
    private static final List<String> REFERENCE_SEQUENCES = List.of(
            "expense_ref_seq",
            "advance_ref_seq",
            "purchase_ref_seq");

    private final DataSource dataSource;

    public DatabaseCleaner(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Empties every application table and rewinds the reference sequences.
     *
     * @throws IllegalStateException when the database cannot be cleaned, so that a broken
     *                               fixture fails loudly instead of leaking rows into the next
     *                               class
     */
    public void clean() {
        try (Connection connection = this.dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            this.requireH2(connection);
            final List<String> tables = this.truncatableTables(connection);

            // Foreign keys make the order matter; suspending the checks avoids having to
            // topologically sort fifteen tables that a future V13 could reorder anyway.
            statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
            try {
                for (final String table : tables) {
                    statement.execute("TRUNCATE TABLE \"" + table + "\" RESTART IDENTITY");
                }
            } finally {
                statement.execute("SET REFERENTIAL_INTEGRITY TRUE");
            }

            for (final String sequence : REFERENCE_SEQUENCES) {
                statement.execute("ALTER SEQUENCE " + sequence + " RESTART WITH 1");
            }
        } catch (final SQLException ex) {
            throw new IllegalStateException("Failed to clean the shared test database", ex);
        }
    }

    /**
     * Reads the table list from JDBC metadata rather than hard-coding it, so a table added by a
     * future migration is cleaned without touching this class.
     */
    private List<String> truncatableTables(final Connection connection) throws SQLException {
        final DatabaseMetaData metaData = connection.getMetaData();
        final List<String> tables = new ArrayList<>();

        try (ResultSet result = metaData.getTables(connection.getCatalog(), connection.getSchema(),
                "%", new String[]{"TABLE"})) {
            while (result.next()) {
                final String table = result.getString("TABLE_NAME");
                if (!PRESERVED_TABLES.contains(table.toLowerCase(Locale.ROOT))) {
                    tables.add(table);
                }
            }
        }
        return tables;
    }

    /**
     * {@code SET REFERENTIAL_INTEGRITY} is H2 syntax. A different engine — the PostgreSQL
     * container of a later work package, say — needs its own statement, so fail with a readable
     * message rather than an obscure SQL error.
     */
    private void requireH2(final Connection connection) throws SQLException {
        final String product = connection.getMetaData().getDatabaseProductName();
        if (!"H2".equalsIgnoreCase(product)) {
            throw new IllegalStateException(
                    "DatabaseCleaner only supports H2, but the test database is " + product);
        }
    }
}
