package com.promoteur.app.postgres;

import com.promoteur.app.AbstractPostgresTest;
import com.promoteur.app.dto.ClientRequest;
import com.promoteur.app.dto.ProjectRequest;
import com.promoteur.app.dto.response.ClientResponse;
import com.promoteur.app.dto.response.ProjectResponse;
import com.promoteur.app.dto.response.SearchHitResponse;
import com.promoteur.app.enums.ProjectStatus;
import com.promoteur.app.service.ClientService;
import com.promoteur.app.service.ProjectService;
import com.promoteur.app.service.SearchService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the global search (UX-08) against PostgreSQL, with names as they are really written.
 *
 * <p>The search is a {@code lower(x) like :pattern} in {@code ClientRepository} and
 * {@code SupplierRepository}. On H2 that is exercised with unaccented fixtures, so it proves
 * nothing about how a Tunisian name behaves: collation and case folding are the database's
 * business, and production's database is PostgreSQL.</p>
 *
 * <p>These tests state the behaviour the code <b>has</b>, not the behaviour one might want. The
 * accent-insensitivity gap they document is a finding to decide on, not a bug to patch here:
 * fixing it means {@code unaccent} or a normalised column, so a migration and a decision.</p>
 */
class PostgresSearchTest extends AbstractPostgresTest {

    @Autowired
    private SearchService searchService;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private ClientService clientService;

    private ProjectResponse residence;
    private ProjectResponse other;

    @BeforeAll
    void seedAccentedClients() {
        this.residence = this.createProject("PG-SRCH-1", "Résidence Ghomrassen");
        this.other = this.createProject("PG-SRCH-2", "Résidence Tataouine");

        this.createClient(this.residence, "Béchir Ben Salah");
        this.createClient(this.residence, "Amira Trabelsi");
        this.createClient(this.other, "Néjib Ferchichi");
    }

    @Test
    @DisplayName("a lower-cased search finds a name written with capitals")
    void aLowerCasedSearchFindsANameWrittenWithCapitals() {
        final List<SearchHitResponse> hits = this.searchService.search("trabelsi", this.residence.id());

        assertThat(hits).anySatisfy(hit -> {
            assertThat(hit.type()).isEqualTo("CLIENT");
            assertThat(hit.label()).isEqualTo("Amira Trabelsi");
        });
    }

    @Test
    @DisplayName("an accented search term finds the accented name it was typed for")
    void anAccentedSearchTermFindsTheAccentedName() {
        assertThat(this.searchService.search("béchir", this.residence.id()))
                .anySatisfy(hit -> assertThat(hit.label()).isEqualTo("Béchir Ben Salah"));
        assertThat(this.searchService.search("BÉCHIR", this.residence.id()))
                .anySatisfy(hit -> assertThat(hit.label()).isEqualTo("Béchir Ben Salah"));
    }

    @Test
    @DisplayName("an unaccented search term does not find an accented name")
    void anUnaccentedSearchTermDoesNotFindAnAccentedName() {
        // Documented gap, not a wish: lower('Béchir') is 'béchir', and 'béchir' does not match
        // '%bechir%'. Someone typing without accents — the common case on an AZERTY keyboard in
        // a hurry — silently gets nothing. Fixing it needs unaccent or a normalised column,
        // hence a migration; see backend/README.md.
        assertThat(this.searchService.search("bechir", this.residence.id()))
                .noneSatisfy(hit -> assertThat(hit.label()).isEqualTo("Béchir Ben Salah"));
        assertThat(this.searchService.search("nejib", this.other.id()))
                .noneSatisfy(hit -> assertThat(hit.label()).isEqualTo("Néjib Ferchichi"));
    }

    @Test
    @DisplayName("the search stays bounded to the project in scope")
    void theSearchStaysBoundedToTheProjectInScope() {
        assertThat(this.searchService.search("béchir", this.other.id()))
                .noneSatisfy(hit -> assertThat(hit.label()).isEqualTo("Béchir Ben Salah"));

        assertThat(this.searchService.search("béchir", null))
                .anySatisfy(hit -> assertThat(hit.label()).isEqualTo("Béchir Ben Salah"));
    }

    @Test
    @DisplayName("a single character is too short to search")
    void aSingleCharacterIsTooShortToSearch() {
        assertThat(this.searchService.search("B", this.residence.id())).isEmpty();
    }

    private ClientResponse createClient(final ProjectResponse project, final String fullName) {
        final ClientRequest request = new ClientRequest();
        request.setFullName(fullName);
        request.setProjectId(project.id());
        return this.clientService.create(request);
    }

    private ProjectResponse createProject(final String code, final String name) {
        final ProjectRequest request = new ProjectRequest();
        request.setCode(code);
        request.setName(name);
        request.setStatus(ProjectStatus.IN_PROGRESS);
        return this.projectService.create(request);
    }
}
