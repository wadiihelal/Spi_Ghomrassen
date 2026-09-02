package com.promoteur.app.config;

import com.promoteur.app.repository.ApartmentRepository;
import com.promoteur.app.repository.ClientAdvanceRepository;
import com.promoteur.app.repository.ClientPurchaseRepository;
import com.promoteur.app.repository.ClientRepository;
import com.promoteur.app.repository.ProjectRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Counterpart to {@link StartupSeedTest}: with the {@code demo} profile active the fictitious
 * business data is loaded exactly as it was before DATA-02 split the seeder in two.
 */
@SpringBootTest
@ActiveProfiles({"test", "demo"})
// Its own in-memory database: the fictitious data must not leak into StartupSeedTest, which
// runs in the same JVM and asserts that nothing but reference data exists.
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:spi_ghomrassen_test_demo;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class DemoProfileSeedTest {

    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private ApartmentRepository apartmentRepository;
    @Autowired
    private ClientPurchaseRepository clientPurchaseRepository;
    @Autowired
    private ClientAdvanceRepository clientAdvanceRepository;

    @Test
    @DisplayName("activating the demo profile loads the fictitious residences, buyers and contracts")
    void activatingTheDemoProfileLoadsFictitiousBusinessData() {
        assertThat(projectRepository.count()).isPositive();
        assertThat(clientRepository.count()).isPositive();
        assertThat(apartmentRepository.count()).isPositive();
        assertThat(clientPurchaseRepository.count()).isPositive();
        assertThat(clientAdvanceRepository.count()).isPositive();
    }

    @Test
    @DisplayName("the demo residences are created with their fictitious project codes")
    void theDemoResidencesAreCreatedWithTheirFictitiousProjectCodes() {
        assertThat(projectRepository.findAll())
                .extracting(project -> project.getCode())
                .contains("SPI-DEMO-RES-A");
    }
}
