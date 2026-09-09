package com.promoteur.app.config;

import com.promoteur.app.apartment.ApartmentRepository;
import com.promoteur.app.client.ClientRepository;
import com.promoteur.app.expense.ExpenseCategoryRepository;
import com.promoteur.app.project.ProjectRepository;
import com.promoteur.app.supplier.SupplierRepository;
import com.promoteur.app.supplier.SupplierTypeOptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the start-up contract established in DATA-01 and DATA-02: the Flyway baseline matches
 * the entity mappings under {@code ddl-auto=validate}, and without the {@code demo} profile the
 * application seeds reference data only.
 */
@SpringBootTest
@ActiveProfiles("test")
class StartupSeedTest {

    @Autowired
    private ExpenseCategoryRepository expenseCategoryRepository;
    @Autowired
    private SupplierTypeOptionRepository supplierTypeOptionRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private ApartmentRepository apartmentRepository;
    @Autowired
    private SupplierRepository supplierRepository;
    @Autowired
    private ReferenceDataInitializer referenceDataInitializer;

    @Test
    @DisplayName("the Flyway baseline satisfies Hibernate schema validation for every entity")
    void flywayBaselineMatchesTheEntityMappings() {
        // Reaching this point means the context started with ddl-auto=validate against the
        // schema built by V1__baseline.sql: any missing table or column would have failed.
        assertThat(expenseCategoryRepository.count()).isPositive();
    }

    @Test
    @DisplayName("starting without the demo profile seeds reference data and no business data")
    void startingWithoutTheDemoProfileSeedsReferenceDataOnly() {
        assertThat(expenseCategoryRepository.count()).isEqualTo(6);
        assertThat(supplierTypeOptionRepository.count()).isEqualTo(5);

        assertThat(projectRepository.count()).isZero();
        assertThat(clientRepository.count()).isZero();
        assertThat(apartmentRepository.count()).isZero();
        assertThat(supplierRepository.count()).isZero();
    }

    @Test
    @DisplayName("the reference data seeder creates only what is missing on a second run")
    void referenceDataSeederIsIdempotentPerItem() {
        this.referenceDataInitializer.run();

        assertThat(expenseCategoryRepository.count()).isEqualTo(6);
        assertThat(supplierTypeOptionRepository.count()).isEqualTo(5);
    }
}
