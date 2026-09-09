package com.promoteur.app.report;

/**
 * Scope requested for a report or an export.
 *
 * @param projectId   project to restrict to; {@code null} means "resolve the active project
 *                    context", not "every project"
 * @param allProjects {@code true} only when the caller explicitly asked for every project
 *                    (query parameter {@code projectId=ALL}); the document must then say so
 * @param year        year to restrict to, or {@code null} for no period restriction
 * @param month       month (1-12) to restrict to; ignored when {@code year} is {@code null}
 */
public record ReportFilter(Long projectId, boolean allProjects, Integer year, Integer month) {

    /**
     * Value of the {@code projectId} query parameter that aggregates across every project.
     */
    public static final String ALL_PROJECTS = "ALL";

    /**
     * Builds a filter from raw query parameters.
     *
     * @param projectId {@code null} for the active project context, {@code ALL} for every
     *                  project, or a project identifier
     * @throws IllegalArgumentException when {@code projectId} is neither {@code ALL} nor a number
     */
    public static ReportFilter of(final String projectId, final Integer year, final Integer month) {
        if (projectId == null || projectId.isBlank()) {
            return new ReportFilter(null, false, year, month);
        }
        if (ALL_PROJECTS.equalsIgnoreCase(projectId.trim())) {
            return new ReportFilter(null, true, year, month);
        }
        try {
            return new ReportFilter(Long.valueOf(projectId.trim()), false, year, month);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Le paramètre projectId doit être un identifiant de projet ou « ALL ».", ex);
        }
    }

    /**
     * Filter covering every project and every period.
     */
    public static ReportFilter unrestricted() {
        return new ReportFilter(null, true, null, null);
    }
}
