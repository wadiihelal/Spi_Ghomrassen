package com.promoteur.app;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Base class for the integration tests that exercise the services against a real database.
 *
 * <h2>Why it exists</h2>
 *
 * <p>Each test class used to pin its own H2 URL through {@code @TestPropertySource}. That URL is
 * part of Spring's context cache key, so the suite started nineteen contexts and ran the twelve
 * Flyway migrations nineteen times. Every class that inherits from here shares <b>one</b>
 * context, because they all declare exactly the same configuration — which is why the property
 * block below lives here and not in the subclasses.</p>
 *
 * <h2>What the shared configuration carries</h2>
 *
 * <ul>
 *   <li>{@code LOCK_TIMEOUT=15000} — {@code AdvanceCeilingTest} lets a second thread wait for the
 *       pessimistic lock of {@code ApartmentRepository} (CONC-01) instead of failing instantly.
 *   <li>{@code hibernate.generate_statistics} — {@code QueryCountTest} counts statements to keep
 *       PERF-01 and PERF-03 from regressing.
 *   <li>{@code app.storage.root} — a per-run directory, so {@code AttachmentTest} (FE-05) never
 *       reads files left by an earlier run.
 * </ul>
 *
 * <p>These three settings are declared here rather than in {@code application-test.properties},
 * which is also read by {@code mvn spring-boot:run -Dspring-boot.run.profiles=test,demo} and is
 * therefore left alone.</p>
 *
 * <h2>Isolation between classes</h2>
 *
 * <p>One database shared by many classes needs the isolation the separate URLs used to give.
 * {@link DatabaseCleaner} empties the application tables <b>before</b> each class rather than
 * after: a class is then guaranteed a clean slate whatever ran before it, including a class that
 * failed halfway through. JUnit runs a superclass {@code @BeforeAll} before the subclass's own,
 * so a fixture seeded in a subclass survives the cleaning.</p>
 *
 * <p>Two classes deliberately stay outside this shared context, with their own URL, because they
 * assert what start-up seeding produces: {@code StartupSeedTest} and
 * {@code DemoProfileSeedTest}. {@code AmountInWordsTest} stays outside too — it is the one pure
 * unit test in the suite and must not gain a Spring context.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(DatabaseCleaner.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:spi_ghomrassen_test_shared;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=15000",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "app.storage.root=${java.io.tmpdir}/spi-ghomrassen-test-shared-${random.uuid}"
})
public abstract class AbstractIntegrationTest {

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @BeforeAll
    void cleanSharedDatabase() {
        this.databaseCleaner.clean();
    }
}
