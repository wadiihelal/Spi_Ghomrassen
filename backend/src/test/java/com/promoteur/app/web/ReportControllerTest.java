package com.promoteur.app.web;

import com.promoteur.app.config.MessageSourceConfig;
import com.promoteur.app.exception.GlobalExceptionHandler;
import com.promoteur.app.report.ReportController;
import com.promoteur.app.report.ReportFilter;
import com.promoteur.app.report.ReportService;
import com.promoteur.app.shared.MessageServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers how a report's scope crosses the HTTP boundary (RPT-01, RPT-02).
 *
 * <p>{@code projectId} carries three different meanings on the wire: absent means "resolve the
 * active project context", {@code ALL} means every project, and a number means that project.
 * Collapse absent into "every project" and a figure for one residence is printed under the name
 * of another — the failure CLAUDE.md warns about in its own words. That translation happens in
 * the controller, from a raw {@code String} to a {@link ReportFilter}, and nothing checked it.</p>
 */
@WebMvcTest(controllers = ReportController.class)
@ActiveProfiles("test")
@Import({GlobalExceptionHandler.class, MessageServiceImpl.class, MessageSourceConfig.class})
class ReportControllerTest {

    private static final String XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportService reportService;

    @Test
    @DisplayName("an absent projectId asks for the active project context, not for every project")
    void anAbsentProjectIdAsksForTheActiveContext() throws Exception {
        given(this.reportService.expensesByCategory(any(), any())).willReturn(Page.empty());

        this.mockMvc.perform(get("/api/reports/expenses/by-category")).andExpect(status().isOk());

        assertThat(this.capturedFilter().allProjects())
                .as("absent means « resolve the active context », never « all projects »")
                .isFalse();
        assertThat(this.capturedFilter().projectId()).isNull();
    }

    @Test
    @DisplayName("projectId=ALL asks for every project explicitly")
    void projectIdAllAsksForEveryProject() throws Exception {
        given(this.reportService.expensesByCategory(any(), any())).willReturn(Page.empty());

        this.mockMvc.perform(get("/api/reports/expenses/by-category").param("projectId", "ALL"))
                .andExpect(status().isOk());

        assertThat(this.capturedFilter().allProjects()).isTrue();
        assertThat(this.capturedFilter().projectId()).isNull();
    }

    @Test
    @DisplayName("an explicit projectId restricts the report to that project alone")
    void anExplicitProjectIdRestrictsToThatProject() throws Exception {
        given(this.reportService.expensesByCategory(any(), any())).willReturn(Page.empty());

        this.mockMvc.perform(get("/api/reports/expenses/by-category")
                        .param("projectId", "7")
                        .param("year", "2026")
                        .param("month", "4"))
                .andExpect(status().isOk());

        assertThat(this.capturedFilter()).isEqualTo(new ReportFilter(7L, false, 2026, 4));
    }

    @Test
    @DisplayName("a projectId that is neither a number nor ALL answers 400 rather than 500")
    void aBogusProjectIdAnswers400RatherThan500() throws Exception {
        this.mockMvc.perform(get("/api/reports/expenses/by-category").param("projectId", "PREMIER"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the excel export is served as a spreadsheet, not as an octet stream")
    void theExcelExportIsServedAsASpreadsheet() throws Exception {
        given(this.reportService.exportReportsExcel(any())).willReturn(new byte[]{'P', 'K', 3, 4});

        this.mockMvc.perform(get("/api/reports/export/excel")
                        .param("projectId", "ALL").param("year", "2026").param("month", "4"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", XLSX));
    }

    @Test
    @DisplayName("the pdf export is served as a pdf")
    void thePdfExportIsServedAsAPdf() throws Exception {
        given(this.reportService.exportReportsPdf(any())).willReturn("%PDF-1.4".getBytes());

        this.mockMvc.perform(get("/api/reports/export/pdf")
                        .param("projectId", "ALL").param("year", "2026").param("month", "4"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE));
    }

    @Test
    @DisplayName("an export without its year answers 400 rather than 500")
    void anExportWithoutItsYearAnswers400RatherThan500() throws Exception {
        // year and month are required on the export endpoints; omitting one used to fall through
        // to the generic branch and answer 500 « Unexpected server error ».
        this.mockMvc.perform(get("/api/reports/export/excel").param("projectId", "ALL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("year")));
    }

    /**
     * The filter the controller built from the query string.
     */
    private ReportFilter capturedFilter() {
        final ArgumentCaptor<ReportFilter> filter = ArgumentCaptor.forClass(ReportFilter.class);
        verify(this.reportService).expensesByCategory(filter.capture(), any());
        return filter.getValue();
    }
}
