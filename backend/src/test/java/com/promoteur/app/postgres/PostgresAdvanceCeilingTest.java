package com.promoteur.app.postgres;

import com.promoteur.app.AbstractPostgresTest;
import com.promoteur.app.advance.ClientAdvance;
import com.promoteur.app.advance.ClientAdvanceRepository;
import com.promoteur.app.advance.ClientAdvanceRequest;
import com.promoteur.app.advance.ClientAdvanceService;
import com.promoteur.app.apartment.ApartmentRequest;
import com.promoteur.app.apartment.ApartmentResponse;
import com.promoteur.app.apartment.ApartmentService;
import com.promoteur.app.client.ClientRequest;
import com.promoteur.app.client.ClientResponse;
import com.promoteur.app.client.ClientService;
import com.promoteur.app.project.ProjectRequest;
import com.promoteur.app.project.ProjectResponse;
import com.promoteur.app.project.ProjectService;
import com.promoteur.app.project.ProjectStatus;
import com.promoteur.app.purchase.ClientPurchaseRequest;
import com.promoteur.app.purchase.ClientPurchaseService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the CONC-01 guarantee against PostgreSQL, the engine production uses.
 *
 * <p>{@code AdvanceCeilingTest} proves on H2 that two advances of 60 % of the contract cannot
 * both be accepted. That proof rests on H2's implementation of
 * {@code @Lock(PESSIMISTIC_WRITE)}, and H2 is not what the promoter's data sits on. PostgreSQL
 * in READ COMMITTED resolves {@code SELECT … FOR UPDATE} differently: the second transaction
 * blocks, then re-reads the row it was made to wait for. Whether the ceiling check still holds
 * is a property of the engine, not of the Java code, so it has to be observed here.</p>
 *
 * <p>The scenario is deliberately identical to the H2 one — same amounts, same latch, same pool
 * of two — so that a difference in outcome can only come from the database.</p>
 */
class PostgresAdvanceCeilingTest extends AbstractPostgresTest {

    private static final BigDecimal CONTRACT_TOTAL = new BigDecimal("100000.000");
    private static final BigDecimal SIXTY_PERCENT = new BigDecimal("60000.000");

    private final AtomicInteger sequence = new AtomicInteger();

    @Autowired
    private ClientAdvanceService clientAdvanceService;
    @Autowired
    private ClientPurchaseService clientPurchaseService;
    @Autowired
    private ApartmentService apartmentService;
    @Autowired
    private ClientService clientService;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private ClientAdvanceRepository clientAdvanceRepository;

    private ProjectResponse project;
    private ClientResponse client;

    @BeforeAll
    void seedProjectAndClient() {
        this.project = this.createProject();
        this.client = this.createClient();
    }

    @Test
    @DisplayName("exactly one of two simultaneous advances is accepted past the ceiling")
    void exactlyOneOfTwoSimultaneousAdvancesIsAccepted() throws Exception {
        final ApartmentResponse apartment = this.createApartmentUnderContract();

        final List<String> outcomes = this.fireTwoAdvancesAtOnce(apartment);

        assertThat(outcomes).filteredOn("accepted"::equals)
                .as("exactly one of the two concurrent advances is accepted")
                .hasSize(1);
    }

    @Test
    @DisplayName("the refusal is the ceiling check, not a lock timeout")
    void theRefusalIsTheCeilingCheckNotALockTimeout() throws Exception {
        final ApartmentResponse apartment = this.createApartmentUnderContract();

        final List<String> outcomes = this.fireTwoAdvancesAtOnce(apartment);

        // The distinction matters: a CannotAcquireLockException or an
        // OptimisticLockingFailureException would mean the ceiling held by accident, because the
        // engine gave up rather than because the rule was applied.
        assertThat(outcomes).filteredOn(outcome -> !"accepted".equals(outcome))
                .singleElement()
                .satisfies(refusal -> {
                    assertThat(refusal).contains("IllegalArgumentException");
                    assertThat(refusal).contains("alors que le plafond est de");
                    assertThat(refusal).doesNotContain("CannotAcquireLock");
                    assertThat(refusal).doesNotContain("OptimisticLocking");
                });
    }

    @Test
    @DisplayName("no advance is lost when both transactions commit")
    void noAdvanceIsLostWhenBothTransactionsCommit() throws Exception {
        final ApartmentResponse apartment = this.createApartmentUnderContract();

        final List<String> outcomes = this.fireTwoAdvancesAtOnce(apartment);
        final long accepted = outcomes.stream().filter("accepted"::equals).count();

        final List<ClientAdvance> stored = this.clientAdvanceRepository.findByApartmentId(apartment.id());
        assertThat(stored).hasSize((int) accepted);
        assertThat(stored).allSatisfy(advance ->
                assertThat(advance.getAmount()).isEqualByComparingTo(SIXTY_PERCENT));
    }

    /**
     * Submits two advances of 60 % of the contract from two threads released together.
     *
     * @return one entry per thread: {@code "accepted"}, or the exception class and message
     */
    private List<String> fireTwoAdvancesAtOnce(final ApartmentResponse apartment) throws Exception {
        final CountDownLatch startTogether = new CountDownLatch(1);
        final Callable<String> attempt = () -> {
            startTogether.await();
            try {
                this.clientAdvanceService.create(this.advanceRequest(apartment, SIXTY_PERCENT));
                return "accepted";
            } catch (final RuntimeException ex) {
                return ex.getClass().getSimpleName() + ": " + ex.getMessage();
            }
        };

        final ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            final Future<String> first = pool.submit(attempt);
            final Future<String> second = pool.submit(attempt);
            startTogether.countDown();
            return List.of(first.get(), second.get());
        } finally {
            pool.shutdownNow();
        }
    }

    private ApartmentResponse createApartmentUnderContract() {
        final ApartmentResponse apartment = this.createApartment();
        this.createPurchase(apartment);
        return apartment;
    }

    private ClientAdvanceRequest advanceRequest(final ApartmentResponse apartment, final BigDecimal amount) {
        final ClientAdvanceRequest request = new ClientAdvanceRequest();
        request.setAdvanceDate(LocalDate.of(2026, 9, 1));
        request.setAmount(amount);
        request.setApartmentId(apartment.id());
        return request;
    }

    private void createPurchase(final ApartmentResponse apartment) {
        final ClientPurchaseRequest request = new ClientPurchaseRequest();
        request.setPurchaseDate(LocalDate.of(2026, 9, 1));
        request.setAssetDescription("Appartement " + apartment.apartmentNumber());
        request.setTotalAmount(CONTRACT_TOTAL);
        request.setPaidAmount(BigDecimal.ZERO);
        request.setClientId(this.client.id());
        request.setProjectId(this.project.id());
        request.setApartmentId(apartment.id());
        this.clientPurchaseService.create(request);
    }

    private ApartmentResponse createApartment() {
        final ApartmentRequest request = new ApartmentRequest();
        request.setApartmentNumber("PG-C-" + this.sequence.incrementAndGet());
        request.setApartmentType("S+2");
        request.setTotalSurface(new BigDecimal("100.000"));
        request.setTotalSalePrice(CONTRACT_TOTAL);
        request.setProjectId(this.project.id());
        request.setAcquirerId(this.client.id());
        return this.apartmentService.create(request);
    }

    private ProjectResponse createProject() {
        final ProjectRequest request = new ProjectRequest();
        request.setCode("PG-CEIL");
        request.setName("Projet plafond PostgreSQL");
        request.setStatus(ProjectStatus.IN_PROGRESS);
        return this.projectService.create(request);
    }

    private ClientResponse createClient() {
        final ClientRequest request = new ClientRequest();
        request.setFullName("Acquéreur plafond PostgreSQL");
        request.setProjectId(this.project.id());
        return this.clientService.create(request);
    }
}
