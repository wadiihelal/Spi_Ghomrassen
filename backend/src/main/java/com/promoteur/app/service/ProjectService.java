package com.promoteur.app.service;

import com.promoteur.app.dto.ProjectRequest;
import com.promoteur.app.dto.response.ProjectResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service exposing project lifecycle and active-context operations.
 */
public interface ProjectService {

    /**
     * Returns projects using the requested pagination.
     */
    Page<ProjectResponse> findAll(Pageable pageable);

    /**
     * Returns a single project by identifier.
     */
    ProjectResponse findById(Long id);

    /**
     * Creates a new project.
     */
    ProjectResponse create(ProjectRequest request);

    /**
     * Updates an existing project.
     */
    ProjectResponse update(Long id, ProjectRequest request);

    /**
     * Deletes a project by identifier.
     */
    void delete(Long id);

    /**
     * Returns the active project context.
     */
    ProjectResponse findActiveContext();

    /**
     * Sets the active project context.
     */
    ProjectResponse setActiveContext(Long id);

    /**
     * Clears any active project context.
     */
    void clearActiveContext();
}
