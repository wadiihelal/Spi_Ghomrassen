package com.promoteur.app.postgres;

import com.promoteur.app.AbstractPostgresTest;
import com.promoteur.app.dto.ExpenseRequest;
import com.promoteur.app.dto.ProjectRequest;
import com.promoteur.app.dto.response.ExpenseResponse;
import com.promoteur.app.dto.response.ProjectResponse;
import com.promoteur.app.enums.ProjectStatus;
import com.promoteur.app.repository.ExpenseCategoryRepository;
import com.promoteur.app.service.ExpenseService;
import com.promoteur.app.service.ProjectService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs DATA-03 — the reference counters — and the money columns against PostgreSQL.
 *
 * <p>References are drawn from a real SQL sequence, read through the Hibernate dialect's
 * {@code SequenceSupport} so the same code serves H2 and PostgreSQL. What the two engines do
 * <em>not</em> necessarily share is how a sequence behaves under concurrency and after a
 * rollback, and whether {@code numeric(19,3)} refuses an oversized amount or quietly rounds it.
 * Both are properties of the engine.</p>
 *
 * <p>The contract DATA-03 actually promises is <b>uniqueness</b>, not contiguity. These tests
 * say so explicitly, so that a future reader does not mistake a gap for a defect.</p>
 *
 * <h2>A production finding this class surfaced</h2>
 *
 * <p>The concurrent test below first failed with
 * {@code HikariPool-1 - Connection is not available, request timed out after 30005ms
 * (total=10, active=10, idle=0, waiting=9)}. The cause is not the sequence: every write holds
 * <b>two</b> connections at once, because {@code AuditLogServiceImpl.create} is
 * {@code REQUIRES_NEW} and opens a second one while the business transaction still holds the
 * first. With Hikari's default of ten, ten concurrent writes take the whole pool and then each
 * wait for an eleventh connection that cannot come.</p>
 *
 * <p>Production sets no pool size, so it runs on that default. The test profile raises the pool
 * (see {@code application-postgres.properties}) so that this class measures what it claims to,
 * and the finding itself is left for a decision — raising the pool, or dropping
 * {@code REQUIRES_NEW} — rather than being fixed inside this work package.</p>
 */
class PostgresReferenceSequenceTest extends AbstractPostgresTest {

    private static final int CONCURRENT_EXPENSES = 100;
    private static final int POOL_SIZE = 10;

    /** numeric(19,3) leaves sixteen digits before the decimal point; this has seventeen. */
    private static final BigDecimal BEYOND_NUMERIC_19_3 = new BigDecimal("10000000000000000.000");

    @Autowired
    private ExpenseService expenseService;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private ExpenseCategoryRepository expenseCategoryRepository;

    private ProjectResponse project;
    private Long categoryId;

    @BeforeAll
    void seedProjectAndCategory() {
        this.project = this.createProject();
        this.categoryId = this.expenseCategoryRepository.findAll().get(0).getId();
    }

    @Test
    @DisplayName("references are allocated without gaps under sequential creation")
    void referencesAreAllocatedWithoutGapsUnderSequentialCreation() {
        final List<String> references = List.of(
                this.createExpense(new BigDecimal("100.000")).reference(),
                this.createExpense(new BigDecimal("200.000")).reference(),
                this.createExpense(new BigDecimal("300.000")).reference());

        assertThat(references).allSatisfy(reference -> assertThat(reference).startsWith("DEP-2026-"));
        assertThat(references).extracting(PostgresReferenceSequenceTest::counterOf)
                .isSorted()
                .satisfies(counters -> assertThat((Integer) counters.get(2) - (Integer) counters.get(0))
                        .as("three consecutive creations advance the counter by two")
                        .isEqualTo(2));
    }

    @Test
    @DisplayName("a rolled back creation may leave a gap but never reuses a number")
    void aRolledBackCreationMayLeaveAGapButNeverReusesANumber() {
        final int before = counterOf(this.createExpense(new BigDecimal("100.000")).reference());

        // The reference is drawn while mapping the entity, so the sequence value is spent before
        // the insert fails on the oversized amount. PostgreSQL sequences are non-transactional:
        // the rollback does not give the number back.
        assertThatThrownBy(() -> this.createExpense(BEYOND_NUMERIC_19_3)).isInstanceOf(RuntimeException.class);

        final int after = counterOf(this.createExpense(new BigDecimal("300.000")).reference());

        assertThat(after).as("the failed creation consumed a number").isGreaterThan(before + 1);
    }

    @Test
    @DisplayName("a hundred concurrent expenses receive a hundred distinct references")
    void aHundredConcurrentExpensesReceiveAHundredDistinctReferences() throws Exception {
        final CountDownLatch startTogether = new CountDownLatch(1);
        final List<Callable<String>> attempts = new ArrayList<>(CONCURRENT_EXPENSES);
        for (int index = 0; index < CONCURRENT_EXPENSES; index++) {
            attempts.add(() -> {
                startTogether.await();
                return this.createExpense(new BigDecimal("10.000")).reference();
            });
        }

        final ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE);
        final List<String> references = new ArrayList<>(CONCURRENT_EXPENSES);
        try {
            final List<Future<String>> futures = new ArrayList<>(CONCURRENT_EXPENSES);
            for (final Callable<String> attempt : attempts) {
                futures.add(pool.submit(attempt));
            }
            startTogether.countDown();
            for (final Future<String> future : futures) {
                references.add(future.get());
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(references).hasSize(CONCURRENT_EXPENSES);
        assertThat(references).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("an amount beyond numeric(19,3) is refused by the database, not silently truncated")
    void anAmountBeyondNumericIsRefusedByTheDatabase() {
        assertThatThrownBy(() -> this.createExpense(BEYOND_NUMERIC_19_3))
                .as("an amount of seventeen integer digits cannot be stored as numeric(19,3)")
                .isInstanceOf(RuntimeException.class);
    }

    /** The five-digit counter at the end of {@code DEP-2026-00042}. */
    private static int counterOf(final String reference) {
        return Integer.parseInt(reference.substring(reference.lastIndexOf('-') + 1));
    }

    private ExpenseResponse createExpense(final BigDecimal amountHt) {
        final ExpenseRequest request = new ExpenseRequest();
        request.setExpenseDate(LocalDate.of(2026, 4, 10));
        request.setDescription("Dépense de séquence");
        request.setAmountHt(amountHt);
        request.setVatRate(new BigDecimal("0.0700"));
        request.setCategoryId(this.categoryId);
        request.setProjectId(this.project.id());
        return this.expenseService.create(request);
    }

    private ProjectResponse createProject() {
        final ProjectRequest request = new ProjectRequest();
        request.setCode("PG-SEQ");
        request.setName("Projet séquences PostgreSQL");
        request.setStatus(ProjectStatus.IN_PROGRESS);
        return this.projectService.create(request);
    }
}
