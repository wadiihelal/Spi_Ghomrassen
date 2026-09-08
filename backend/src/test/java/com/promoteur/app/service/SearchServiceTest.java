package com.promoteur.app.service;

import com.promoteur.app.dto.ApartmentRequest;
import com.promoteur.app.dto.ClientRequest;
import com.promoteur.app.dto.ProjectRequest;
import com.promoteur.app.dto.SupplierRequest;
import com.promoteur.app.dto.response.ProjectResponse;
import com.promoteur.app.dto.response.SearchHitResponse;
import com.promoteur.app.enums.ProjectStatus;
import com.promoteur.app.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the global search (UX-08): a few characters find a client, a lot or a supplier, the
 * project in scope bounds what is found, and the result list stays short.
 */
class SearchServiceTest extends AbstractIntegrationTest {

    @Autowired
    private SearchService searchService;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private ClientService clientService;
    @Autowired
    private ApartmentService apartmentService;
    @Autowired
    private SupplierService supplierService;

    private ProjectResponse first;
    private ProjectResponse second;

    @BeforeAll
    void seed() {
        this.first = this.project("SRCH-1");
        this.second = this.project("SRCH-2");

        this.client(this.first, "Mounir Trabelsi Recherche", "07123456", "22 333 444");
        this.client(this.second, "Mounira Zouari Recherche", "08999999", "55 666 777");
        for (int index = 0; index < 8; index++) {
            this.client(this.first, "Doublon Recherche " + index, null, null);
        }
        this.apartment(this.first, "SR-A41");

        SupplierRequest supplier = new SupplierRequest();
        supplier.setName("Carrelages Recherche du Sud");
        this.supplierService.create(supplier);
    }

    @Test
    @DisplayName("a client is found by a fragment of their name, whatever the case")
    void aClientIsFoundByAFragmentOfTheirName() {
        List<SearchHitResponse> hits = this.searchService.search("mounir t", this.first.id());

        assertThat(hits).anySatisfy(hit -> {
            assertThat(hit.type()).isEqualTo("CLIENT");
            assertThat(hit.label()).isEqualTo("Mounir Trabelsi Recherche");
        });
    }

    @Test
    @DisplayName("a client is found by identity number or phone")
    void aClientIsFoundByIdentityNumberOrPhone() {
        assertThat(this.searchService.search("0712", this.first.id()))
                .extracting(SearchHitResponse::label).contains("Mounir Trabelsi Recherche");
        assertThat(this.searchService.search("22 333", this.first.id()))
                .extracting(SearchHitResponse::label).contains("Mounir Trabelsi Recherche");
    }

    @Test
    @DisplayName("the project in scope bounds what is found")
    void theProjectInScopeBoundsWhatIsFound() {
        List<SearchHitResponse> hits = this.searchService.search("Mounir", this.second.id());

        assertThat(hits).extracting(SearchHitResponse::label)
                .contains("Mounira Zouari Recherche")
                .doesNotContain("Mounir Trabelsi Recherche");
    }

    @Test
    @DisplayName("without a project, every project is searched")
    void withoutAProjectEveryProjectIsSearched() {
        assertThat(this.searchService.search("Mounir", null))
                .extracting(SearchHitResponse::label)
                .contains("Mounir Trabelsi Recherche", "Mounira Zouari Recherche");
    }

    @Test
    @DisplayName("a lot is found by its number")
    void aLotIsFoundByItsNumber() {
        assertThat(this.searchService.search("sr-a4", this.first.id())).anySatisfy(hit -> {
            assertThat(hit.type()).isEqualTo("APARTMENT");
            assertThat(hit.label()).isEqualTo("SR-A41");
            assertThat(hit.detail()).contains("Projet SRCH-1");
        });
    }

    @Test
    @DisplayName("suppliers are found regardless of the project in scope")
    void suppliersAreFoundRegardlessOfTheProject() {
        assertThat(this.searchService.search("carrelages", this.second.id())).anySatisfy(hit -> {
            assertThat(hit.type()).isEqualTo("SUPPLIER");
            assertThat(hit.label()).isEqualTo("Carrelages Recherche du Sud");
        });
    }

    @Test
    @DisplayName("each type returns at most five hits")
    void eachTypeReturnsAtMostFiveHits() {
        List<SearchHitResponse> hits = this.searchService.search("Doublon Recherche", this.first.id());

        assertThat(hits).filteredOn(hit -> hit.type().equals("CLIENT")).hasSize(SearchService.HITS_PER_TYPE);
    }

    @Test
    @DisplayName("a single character or a blank query finds nothing")
    void aSingleCharacterOrABlankQueryFindsNothing() {
        assertThat(this.searchService.search("M", this.first.id())).isEmpty();
        assertThat(this.searchService.search("   ", this.first.id())).isEmpty();
        assertThat(this.searchService.search(null, this.first.id())).isEmpty();
    }

    private ProjectResponse project(String code) {
        ProjectRequest request = new ProjectRequest();
        request.setCode(code);
        request.setName("Projet " + code);
        request.setStatus(ProjectStatus.IN_PROGRESS);
        return this.projectService.create(request);
    }

    private void client(ProjectResponse scope, String name, String cin, String phone) {
        ClientRequest request = new ClientRequest();
        request.setFullName(name);
        request.setCinOrFiscalId(cin);
        request.setPhone(phone);
        request.setProjectId(scope.id());
        this.clientService.create(request);
    }

    private void apartment(ProjectResponse scope, String number) {
        ApartmentRequest request = new ApartmentRequest();
        request.setApartmentNumber(number);
        request.setApartmentType("S+2");
        request.setTotalSurface(new BigDecimal("90.000"));
        request.setTotalSalePrice(new BigDecimal("150000.000"));
        request.setProjectId(scope.id());
        this.apartmentService.create(request);
    }
}
