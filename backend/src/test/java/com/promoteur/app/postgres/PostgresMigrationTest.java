package com.promoteur.app.postgres;

import com.promoteur.app.AbstractPostgresTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confronts the schema with the engine production runs on.
 *
 * <p>The twelve migrations are written in portable SQL and {@code ddl-auto=validate} checks the
 * mapping at every start-up — but until this class, only ever against H2. Nothing here tests a
 * business rule: it tests that the schema PostgreSQL builds is the schema the entities expect,
 * which is the cheapest insurance in the suite. If the context of this class starts at all,
 * Flyway ran and {@code validate} accepted the mapping.</p>
 */
class PostgresMigrationTest extends AbstractPostgresTest {

    /**
     * Migrations delivered so far: V1 to V12.
     */
    private static final int DELIVERED_MIGRATIONS = 12;

    /**
     * The three columns that hold a VAT <em>rate</em> rather than an amount. A rate is a
     * fraction with four decimals (0.1900), so it is {@code numeric(5,4)} and must not be
     * mistaken for money — hence an explicit list rather than a guess on the column name.
     */
    private static final Set<String> RATE_COLUMNS = Set.of("vat_rate", "default_vat_rate", "rate");

    /**
     * Money is counted in millimes: three decimals, as CLAUDE.md requires.
     */
    private static final int MONEY_PRECISION = 19;
    private static final int MONEY_SCALE = 3;

    private static final int RATE_PRECISION = 5;
    private static final int RATE_SCALE = 4;

    private static final List<String> REFERENCE_SEQUENCES =
            List.of("advance_ref_seq", "expense_ref_seq", "purchase_ref_seq");

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("the twelve migrations apply in order on a clean PostgreSQL database")
    void theTwelveMigrationsApplyInOrder() {
        final List<Map<String, Object>> history = this.jdbcTemplate.queryForList(
                "select installed_rank, version, success from flyway_schema_history order by installed_rank");

        assertThat(history).hasSize(DELIVERED_MIGRATIONS);
        assertThat(history).allSatisfy(row -> assertThat(row.get("success")).isEqualTo(true));
        assertThat(history).extracting(row -> row.get("version"))
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");
    }

    @Test
    @DisplayName("every money column is numeric(19,3) and every rate column numeric(5,4)")
    void everyMoneyColumnIsNumericNineteenThree() {
        final List<Map<String, Object>> columns = this.jdbcTemplate.queryForList("""
                select table_name, column_name, numeric_precision, numeric_scale
                from information_schema.columns
                where table_schema = 'public' and data_type = 'numeric'
                order by table_name, column_name
                """);

        assertThat(columns).isNotEmpty();
        assertThat(columns).allSatisfy(column -> {
            final String name = (String) column.get("column_name");
            final int expectedPrecision = RATE_COLUMNS.contains(name) ? RATE_PRECISION : MONEY_PRECISION;
            final int expectedScale = RATE_COLUMNS.contains(name) ? RATE_SCALE : MONEY_SCALE;

            assertThat(column.get("numeric_precision"))
                    .as("precision of %s.%s", column.get("table_name"), name)
                    .isEqualTo(expectedPrecision);
            assertThat(column.get("numeric_scale"))
                    .as("scale of %s.%s", column.get("table_name"), name)
                    .isEqualTo(expectedScale);
        });
    }

    @Test
    @DisplayName("the version column exists on every table an entity maps")
    void theVersionColumnExistsOnEveryMappedTable() {
        final Set<String> mappedTables = this.mappedTableNames();
        final Set<String> tablesWithVersion = Set.copyOf(this.jdbcTemplate.queryForList("""
                select table_name from information_schema.columns
                where table_schema = 'public' and column_name = 'version'
                """, String.class));

        // V5 added the column to the twelve tables that existed then; V8, V9 and V10 declared it
        // on the three they created. Reading the list from the metamodel rather than from V5
        // means a sixteenth entity cannot slip through without its optimistic lock (CONC-01).
        assertThat(mappedTables).hasSize(15);
        assertThat(tablesWithVersion).containsAll(mappedTables);
    }

    @Test
    @DisplayName("the version column is a non-nullable bigint, so no row can skip its lock")
    void theVersionColumnIsANonNullableBigint() {
        // Restricted to the mapped tables on purpose: flyway_schema_history carries a column
        // called `version` too, holding the migration number as a varchar.
        final Set<String> mappedTables = this.mappedTableNames();
        final List<Map<String, Object>> versionColumns = this.jdbcTemplate.queryForList("""
                        select table_name, data_type, is_nullable from information_schema.columns
                        where table_schema = 'public' and column_name = 'version'
                        """)
                .stream()
                .filter(column -> mappedTables.contains((String) column.get("table_name")))
                .toList();

        assertThat(versionColumns).hasSize(mappedTables.size());
        assertThat(versionColumns).allSatisfy(column -> {
            assertThat(column.get("data_type"))
                    .as("type of %s.version", column.get("table_name")).isEqualTo("bigint");
            assertThat(column.get("is_nullable"))
                    .as("nullability of %s.version", column.get("table_name")).isEqualTo("NO");
        });
    }

    @Test
    @DisplayName("the three reference sequences exist and start at one")
    void theThreeReferenceSequencesExistAndStartAtOne() {
        final List<Map<String, Object>> sequences = this.jdbcTemplate.queryForList("""
                select sequence_name, start_value, increment
                from information_schema.sequences
                where sequence_schema = 'public'
                order by sequence_name
                """);

        assertThat(sequences).extracting(row -> row.get("sequence_name"))
                .containsExactlyElementsOf(REFERENCE_SEQUENCES);
        assertThat(sequences).allSatisfy(sequence -> {
            assertThat(sequence.get("start_value"))
                    .as("start of %s", sequence.get("sequence_name")).hasToString("1");
            assertThat(sequence.get("increment"))
                    .as("increment of %s", sequence.get("sequence_name")).hasToString("1");
        });
    }

    /**
     * Table names as the entities declare them, so the assertions follow the mapping.
     */
    private Set<String> mappedTableNames() {
        return this.entityManager.getMetamodel().getEntities().stream()
                .map(EntityType::getJavaType)
                .map(type -> type.getAnnotation(Table.class))
                .map(Table::name)
                .collect(Collectors.toUnmodifiableSet());
    }
}
