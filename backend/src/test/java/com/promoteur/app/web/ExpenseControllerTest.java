package com.promoteur.app.web;

import com.promoteur.app.config.MessageSourceConfig;
import com.promoteur.app.controller.ExpenseController;
import com.promoteur.app.dto.ListFilter;
import com.promoteur.app.dto.response.ExpenseResponse;
import com.promoteur.app.enums.PaymentMethod;
import com.promoteur.app.exception.GlobalExceptionHandler;
import com.promoteur.app.service.ExpenseService;
import com.promoteur.app.service.impl.MessageServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the CRUD-and-filters pattern the other eighteen controllers repeat (API-02, PERF-02).
 *
 * <p>Two contracts live in a controller and nowhere else: the HTTP conventions — 201 with a
 * {@code Location} header, 400 with a {@code details} map — and the faithful transport of the
 * ten {@link ListFilter} parameters to the service. A filter silently dropped between the query
 * string and the service is invisible to a service test and shows up as a page of the wrong
 * rows, which is exactly what PERF-02 set out to remove.</p>
 *
 * <p>The service is mocked here on purpose: what it computes is covered by its own tests, and
 * this slice is about the boundary. It is the only place in the suite where Mockito is allowed.</p>
 */
@WebMvcTest(controllers = ExpenseController.class)
@ActiveProfiles("test")
@Import({GlobalExceptionHandler.class, MessageServiceImpl.class, MessageSourceConfig.class})
class ExpenseControllerTest {

    private static final String VALID_PAYLOAD = """
            {
              "expenseDate": "2026-04-10",
              "description": "Frais de dossier baladiya",
              "amountHt": 1000.000,
              "vatRate": 0.0700,
              "paymentMethod": "BANK_TRANSFER",
              "categoryId": 1,
              "projectId": 1
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExpenseService expenseService;

    @Test
    @DisplayName("a valid creation answers 201 with a Location header pointing at the new expense")
    void aValidCreationAnswers201WithALocationHeader() throws Exception {
        given(this.expenseService.create(any())).willReturn(this.expense(42L));

        this.mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/expenses/42")))
                .andExpect(jsonPath("$.reference").value("DEP-2026-00042"))
                .andExpect(jsonPath("$.amountTtc").value(1070.000));
    }

    @Test
    @DisplayName("a creation without a net amount answers 400 naming the field")
    void aCreationWithoutANetAmountAnswers400NamingTheField() throws Exception {
        this.mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD.replace("\"amountHt\": 1000.000,", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.amountHt").exists());
    }

    @Test
    @DisplayName("a negative net amount answers 400 naming the field")
    void aNegativeNetAmountAnswers400NamingTheField() throws Exception {
        this.mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD.replace("1000.000", "-1000.000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.amountHt").exists());
    }

    @Test
    @DisplayName("a blank description answers 400 naming the field")
    void aBlankDescriptionAnswers400NamingTheField() throws Exception {
        this.mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD.replace("Frais de dossier baladiya", "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.description").exists());
    }

    @Test
    @DisplayName("every list filter reaches the service exactly as it was queried")
    void everyListFilterReachesTheServiceExactlyAsQueried() throws Exception {
        given(this.expenseService.findAll(any(), any())).willReturn(Page.empty());

        this.mockMvc.perform(get("/api/expenses")
                        .param("projectId", "1")
                        .param("clientId", "2")
                        .param("supplierId", "3")
                        .param("categoryId", "4")
                        .param("apartmentId", "5")
                        .param("paymentStatus", "PARTIALLY_PAID")
                        .param("paymentMethod", "CHECK")
                        .param("dateFrom", "2026-01-01")
                        .param("dateTo", "2026-12-31")
                        .param("search", "baladiya"))
                .andExpect(status().isOk());

        final ArgumentCaptor<ListFilter> filter = ArgumentCaptor.forClass(ListFilter.class);
        org.mockito.Mockito.verify(this.expenseService).findAll(filter.capture(), any());

        // Asserted as a whole record rather than field by field: a parameter wired to the wrong
        // constructor position — clientId landing in supplierId, say — would pass ten separate
        // "is not null" checks.
        assertThat(filter.getValue()).isEqualTo(new ListFilter(1L, 2L, 3L, 4L, 5L,
                "PARTIALLY_PAID", "CHECK", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                "baladiya"));
    }

    @Test
    @DisplayName("an absent filter reaches the service as null rather than as a blank string")
    void anAbsentFilterReachesTheServiceAsNull() throws Exception {
        given(this.expenseService.findAll(any(), any())).willReturn(Page.empty());

        this.mockMvc.perform(get("/api/expenses")).andExpect(status().isOk());

        final ArgumentCaptor<ListFilter> filter = ArgumentCaptor.forClass(ListFilter.class);
        org.mockito.Mockito.verify(this.expenseService).findAll(filter.capture(), any());

        assertThat(filter.getValue()).isEqualTo(ListFilter.none());
    }

    @Test
    @DisplayName("paging and sorting reach the service intact")
    void pagingAndSortingReachTheServiceIntact() throws Exception {
        given(this.expenseService.findAll(any(), any())).willReturn(Page.empty());

        this.mockMvc.perform(get("/api/expenses")
                        .param("page", "2")
                        .param("size", "50")
                        .param("sort", "expenseDate,desc"))
                .andExpect(status().isOk());

        final ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(this.expenseService).findAll(any(), pageable.capture());

        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "expenseDate"));
    }

    @Test
    @DisplayName("a malformed date answers 400 rather than 500")
    void aMalformedDateAnswers400RatherThan500() throws Exception {
        this.mockMvc.perform(get("/api/expenses").param("dateFrom", "2026-13-45"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("an unknown payment status answers 400 rather than 500")
    void anUnknownPaymentStatusAnswers400RatherThan500() throws Exception {
        // Refused by the compact constructor of ListFilter, inside the controller method: the
        // point is that the handler turns that refusal into a 400 and not a data-access 500.
        this.mockMvc.perform(get("/api/expenses").param("paymentStatus", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        org.hamcrest.Matchers.containsString("Statut de paiement inconnu")));
    }

    @Test
    @DisplayName("the deprecated by-criterion routes still answer while they are being retired")
    void theDeprecatedByCriterionRoutesStillAnswer() throws Exception {
        given(this.expenseService.findByCategory(org.mockito.ArgumentMatchers.eq(4L), any()))
                .willReturn(new PageImpl<>(List.of(this.expense(1L)), PageRequest.of(0, 20), 1));
        given(this.expenseService.findByProject(org.mockito.ArgumentMatchers.eq(1L), any()))
                .willReturn(new PageImpl<>(List.of(this.expense(2L)), PageRequest.of(0, 20), 1));

        this.mockMvc.perform(get("/api/expenses/by-category/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        this.mockMvc.perform(get("/api/expenses/by-project/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("a deletion answers 204 with no body")
    void aDeletionAnswers204WithNoBody() throws Exception {
        this.mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/expenses/42"))
                .andExpect(status().isNoContent())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(""));
    }

    private ExpenseResponse expense(final Long id) {
        return new ExpenseResponse(id, "DEP-2026-00042", LocalDate.of(2026, 4, 10),
                "Frais de dossier baladiya", new BigDecimal("1000.000"), new BigDecimal("0.0700"),
                new BigDecimal("70.000"), new BigDecimal("1070.000"), PaymentMethod.BANK_TRANSFER,
                "FAC-001", null, null, null, 1L, "Frais Baladiya", 1L, "Résidence Ghomrassen",
                null, null);
    }
}
