package com.promoteur.app.web;

import com.promoteur.app.config.MessageSourceConfig;
import com.promoteur.app.exception.GlobalExceptionHandler;
import com.promoteur.app.search.SearchController;
import com.promoteur.app.search.SearchHitResponse;
import com.promoteur.app.search.SearchService;
import com.promoteur.app.shared.MessageServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the contract of the global search endpoint (UX-08).
 *
 * <p>The search box in the top bar calls this on every other keystroke, so two things matter at
 * the boundary: that the project in scope is passed through — a hit from another residence is
 * worse than no hit — and that a missing or too-short term is handled without a 500.</p>
 */
@WebMvcTest(controllers = SearchController.class)
@ActiveProfiles("test")
@Import({GlobalExceptionHandler.class, MessageServiceImpl.class, MessageSourceConfig.class})
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchService searchService;

    @Test
    @DisplayName("a query and the project in scope both reach the service")
    void aQueryAndTheProjectInScopeBothReachTheService() throws Exception {
        given(this.searchService.search("béchir", 7L)).willReturn(List.of(
                new SearchHitResponse("CLIENT", 3L, "Béchir Ben Salah", "22 333 444")));

        this.mockMvc.perform(get("/api/search").param("q", "béchir").param("projectId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("CLIENT"))
                .andExpect(jsonPath("$[0].label").value("Béchir Ben Salah"));

        verify(this.searchService).search("béchir", 7L);
    }

    @Test
    @DisplayName("an absent projectId searches across every project")
    void anAbsentProjectIdSearchesAcrossEveryProject() throws Exception {
        given(this.searchService.search(eq("trabelsi"), isNull())).willReturn(List.of());

        this.mockMvc.perform(get("/api/search").param("q", "trabelsi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(this.searchService).search("trabelsi", null);
    }

    @Test
    @DisplayName("a term too short comes back as an empty list, not as an error")
    void aTermTooShortComesBackAsAnEmptyList() throws Exception {
        given(this.searchService.search(eq("b"), isNull())).willReturn(List.of());

        this.mockMvc.perform(get("/api/search").param("q", "b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("a missing query parameter answers 400 rather than 500")
    void aMissingQueryParameterAnswers400RatherThan500() throws Exception {
        this.mockMvc.perform(get("/api/search"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a non-numeric projectId answers 400 rather than 500")
    void aNonNumericProjectIdAnswers400RatherThan500() throws Exception {
        this.mockMvc.perform(get("/api/search").param("q", "trabelsi").param("projectId", "sept"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("hits are returned in the order the service grouped them")
    void hitsAreReturnedInTheOrderTheServiceGroupedThem() throws Exception {
        given(this.searchService.search(eq("sud"), isNull())).willReturn(List.of(
                new SearchHitResponse("CLIENT", 1L, "Client du Sud", null),
                new SearchHitResponse("APARTMENT", 2L, "A-12", "Résidence Ghomrassen"),
                new SearchHitResponse("SUPPLIER", 3L, "Carrelages du Sud", null)));

        this.mockMvc.perform(get("/api/search").param("q", "sud"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("CLIENT"))
                .andExpect(jsonPath("$[1].type").value("APARTMENT"))
                .andExpect(jsonPath("$[2].type").value("SUPPLIER"));
    }
}
