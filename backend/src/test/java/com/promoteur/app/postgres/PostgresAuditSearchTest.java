package com.promoteur.app.postgres;

import com.promoteur.app.AbstractPostgresTest;
import com.promoteur.app.audit.AuditLogResponse;
import com.promoteur.app.audit.AuditLogService;
import com.promoteur.app.project.ProjectRequest;
import com.promoteur.app.project.ProjectService;
import com.promoteur.app.project.ProjectStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the «&nbsp;Journal des opérations&nbsp;» search against PostgreSQL.
 *
 * <h2>Why it exists</h2>
 *
 * <p>The journal answered 500 on the test server on 17/09/2026 — with no filter at all, unlike
 * the échéancier, which only failed once a date bound was supplied. Its query compares four
 * optional parameters with {@code (:param is null or column = :param)}, the shape PostgreSQL
 * rejects when it cannot infer the parameter's type. H2 accepts it, so the whole suite stayed
 * green and nothing covered this query on the production engine.</p>
 *
 * <p>The unfiltered case is the one the screen opens on, so it comes first here.</p>
 */
class PostgresAuditSearchTest extends AbstractPostgresTest {

    private static final int PAGE_SIZE = 25;

    @Autowired
    private AuditLogService auditLogService;
    @Autowired
    private ProjectService projectService;

    /**
     * Creating a project writes an audit entry through the normal service path, so the journal
     * has something to find.
     */
    @BeforeAll
    void seedOneAuditedOperation() {
        final ProjectRequest request = new ProjectRequest();
        request.setCode("PG-AUDIT");
        request.setName("Projet journal PostgreSQL");
        request.setStatus(ProjectStatus.IN_PROGRESS);
        this.projectService.create(request);
    }

    @Test
    @DisplayName("the journal opens without any filter")
    void theJournalOpensWithoutAnyFilter() {
        assertThat(this.search(null, null, null, null).getContent())
                .extracting(AuditLogResponse::entityType)
                .contains("PROJECT");
    }

    @Test
    @DisplayName("the journal filters on the kind of record")
    void theJournalFiltersOnTheKindOfRecord() {
        assertThat(this.search("PROJECT", null, null, null).getContent())
                .isNotEmpty()
                .allSatisfy(entry -> assertThat(entry.entityType()).isEqualTo("PROJECT"));

        assertThat(this.search("EXPENSE", null, null, null).getContent())
                .allSatisfy(entry -> assertThat(entry.entityType()).isEqualTo("EXPENSE"));
    }

    @Test
    @DisplayName("the journal filters on who acted")
    void theJournalFiltersOnWhoActed() {
        // No authentication yet (SEC-01): every entry is stamped « system ».
        assertThat(this.search(null, "system", null, null).getContent()).isNotEmpty();
        assertThat(this.search(null, "personne", null, null).getContent()).isEmpty();
    }

    @Test
    @DisplayName("the journal filters on a period, bounds included")
    void theJournalFiltersOnAPeriod() {
        final LocalDate today = LocalDate.now();

        assertThat(this.search(null, null, today, today).getContent())
                .as("an operation recorded today is inside a window that starts and ends today")
                .isNotEmpty();
        assertThat(this.search(null, null, today.plusDays(1), null).getContent())
                .as("nothing has been recorded tomorrow")
                .isEmpty();
        assertThat(this.search(null, null, null, today.minusDays(1)).getContent())
                .as("nothing was recorded before today in a freshly cleaned database")
                .isEmpty();
    }

    @Test
    @DisplayName("the journal combines a kind, an actor and a period in one search")
    void theJournalCombinesEveryFilter() {
        final LocalDate today = LocalDate.now();

        assertThat(this.search("PROJECT", "system", today, today).getContent())
                .isNotEmpty()
                .allSatisfy(entry -> assertThat(entry.entityType()).isEqualTo("PROJECT"));
    }

    private Page<AuditLogResponse> search(final String entityType, final String actor,
                                          final LocalDate from, final LocalDate to) {
        return this.auditLogService.search(entityType, actor, from, to, PageRequest.of(0, PAGE_SIZE));
    }
}
