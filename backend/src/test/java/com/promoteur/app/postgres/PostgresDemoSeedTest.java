package com.promoteur.app.postgres;

import com.promoteur.app.AbstractPostgresTest;
import com.promoteur.app.advance.ClientAdvanceRepository;
import com.promoteur.app.apartment.ApartmentRepository;
import com.promoteur.app.client.ClientRepository;
import com.promoteur.app.config.DemoDataInitializer;
import com.promoteur.app.expense.ExpenseRepository;
import com.promoteur.app.invoice.SupplierInvoiceRepository;
import com.promoteur.app.project.Project;
import com.promoteur.app.project.ProjectRepository;
import com.promoteur.app.purchase.ClientPurchaseRepository;
import com.promoteur.app.schedule.PaymentInstallmentRepository;
import com.promoteur.app.supplier.SupplierRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the demo seed against PostgreSQL 16 — what the test server (Lot 1 bis) actually does.
 *
 * <p>{@code DemoProfileSeedTest} proves on H2 that the {@code demo} profile loads the fictitious
 * residences and buyers. The test server starts the very same seeder on the production engine,
 * and starts it again at every container restart: a dialect difference, or a duplicate produced
 * by the second run, would surface there — on the client's screen. Both are checked here first.</p>
 *
 * <p>The seeder also runs when this class's context starts, over whatever the shared container
 * holds at that moment. The base class then empties every business table, so the assertions
 * below rest on an explicit run over a clean database: the first start-up of the test server.</p>
 */
@ActiveProfiles("demo")
class PostgresDemoSeedTest extends AbstractPostgresTest {

    /**
     * The five residences {@code DemoDataInitializer.ensureDemoResidenceData} reconciles on
     * every start-up — the ones the client will see first on the board.
     */
    private static final List<String> DEMO_RESIDENCE_CODES = List.of(
            "SPI-DEMO-RES-A", "SPI-DEMO-RES-B", "SPI-DEMO-RES-C", "SPI-DEMO-RES-D", "SPI-DEMO-RES-E");

    @Autowired
    private DemoDataInitializer demoDataInitializer;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private SupplierRepository supplierRepository;
    @Autowired
    private ApartmentRepository apartmentRepository;
    @Autowired
    private ClientPurchaseRepository clientPurchaseRepository;
    @Autowired
    private ClientAdvanceRepository clientAdvanceRepository;
    @Autowired
    private PaymentInstallmentRepository paymentInstallmentRepository;
    @Autowired
    private ExpenseRepository expenseRepository;
    @Autowired
    private SupplierInvoiceRepository supplierInvoiceRepository;

    /**
     * Runs after the base class has emptied the database: exactly the situation of the first
     * {@code docker compose up} on the test server.
     */
    @BeforeAll
    void seedLikeTheFirstStartUp() {
        this.demoDataInitializer.run();
    }

    @Test
    @DisplayName("the demo seed loads the fictitious residences, buyers and contracts on PostgreSQL")
    void theDemoSeedLoadsOnPostgres() {
        assertThat(this.projectRepository.findAll())
                .extracting(Project::getCode)
                .containsAll(DEMO_RESIDENCE_CODES);

        assertThat(this.rowCounts())
                .as("every business table the demonstration relies on is populated")
                .allSatisfy((table, rows) -> assertThat(rows).as(table).isPositive());
    }

    @Test
    @DisplayName("restarting the application re-runs the seed without duplicating a single row")
    void restartingDoesNotDuplicateDemoData() {
        final Map<String, Long> afterFirstStart = this.rowCounts();

        // A container restart runs every CommandLineRunner again; the seeder must recognise
        // its own rows — by project code, client e-mail, apartment number and purchase
        // reference — rather than insert them twice.
        this.demoDataInitializer.run();

        assertThat(this.rowCounts()).isEqualTo(afterFirstStart);
    }

    /**
     * Row counts of every table the demonstration shows, keyed by a readable name so that a
     * failing comparison says which table drifted.
     */
    private Map<String, Long> rowCounts() {
        final Map<String, Long> counts = new TreeMap<>();
        counts.put("projects", this.projectRepository.count());
        counts.put("clients", this.clientRepository.count());
        counts.put("suppliers", this.supplierRepository.count());
        counts.put("apartments", this.apartmentRepository.count());
        counts.put("purchases", this.clientPurchaseRepository.count());
        counts.put("advances", this.clientAdvanceRepository.count());
        counts.put("installments", this.paymentInstallmentRepository.count());
        counts.put("expenses", this.expenseRepository.count());
        counts.put("supplier invoices", this.supplierInvoiceRepository.count());
        return counts;
    }
}
