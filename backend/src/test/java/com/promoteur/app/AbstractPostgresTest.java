package com.promoteur.app;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for the tests that run against PostgreSQL 16 — the engine production actually uses.
 *
 * <h2>Why it exists</h2>
 *
 * <p>The whole suite runs on H2. The twelve migrations, {@code ddl-auto=validate}, the
 * pessimistic lock behind the advance ceiling (CONC-01), the reference sequences (DATA-03) and
 * the {@code numeric(19,3)} money columns had therefore never been confronted with the real
 * dialect. A green H2 test proves the code works on H2.</p>
 *
 * <h2>Running these tests</h2>
 *
 * <p>They need a Docker daemon, so they carry {@code @Tag("postgres")} and surefire excludes
 * that tag by default: {@code mvn verify} stays usable without Docker, and
 * {@code mvn verify -Ppostgres} runs them.</p>
 *
 * <p>The container is {@code static}, so one PostgreSQL instance serves every class that
 * inherits from here — the same reasoning as {@link AbstractIntegrationTest}, applied to a
 * container instead of a context.</p>
 *
 * <p>{@code withReuse(true)} keeps the container alive between runs, but only for a developer
 * who has opted in with {@code testcontainers.reuse.enable=true} in
 * {@code ~/.testcontainers.properties} (see {@code README.md}). Nothing here depends on it:
 * without that flag Ryuk removes the container when the JVM exits.</p>
 *
 * <h2>Why the container is started by hand</h2>
 *
 * <p>The obvious spelling — {@code @Testcontainers} on the class and {@code @Container} on the
 * field — fails here with {@code "Mapped port can only be obtained after the container is
 * started"}. Both {@code SpringExtension} and {@code TestcontainersExtension} hook
 * {@code beforeAll}, and Spring's runs first, so the datasource asks the container for its port
 * before the extension has started it. Starting it in a static initialiser removes the
 * ordering question altogether: the container is up before any Spring machinery observes the
 * class.</p>
 */
@SpringBootTest
@ActiveProfiles({"test", "postgres"})
@Import(DatabaseCleaner.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("postgres")
public abstract class AbstractPostgresTest {

    /** Same major version as docker-compose.yml, so the tests exercise what really runs. */
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("spi_ghomrassen")
                    .withUsername("spi")
                    .withPassword("spi")
                    .withReuse(true);

    static {
        POSTGRES.start();
    }

    @Autowired
    private DatabaseCleaner databaseCleaner;

    /**
     * One container serves every class here, so the isolation the container alone cannot give
     * comes from the same cleaner the H2 suite uses — see {@link AbstractIntegrationTest} for
     * why the cleaning happens before the class rather than after it.
     */
    @BeforeAll
    void cleanContainerDatabase() {
        this.databaseCleaner.clean();
    }
}
