package com.promoteur.app.service;

import com.promoteur.app.dto.ProjectRequest;
import com.promoteur.app.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service exposing project lifecycle and active-context operations.
 */
public interface ProjectService {

    /**
     * Returns projects using the requested pagination.
     */
    Page<Project> findAll(Pageable pageable);

    /**
     * Returns a single project by identifier.
     */
    Project findById(Long id);

    /**
     * Creates a new project.
     */
    Project create(ProjectRequest request);

    /**
     * Updates an existing project.
     */
    Project update(Long id, ProjectRequest request);

    /**
     * Deletes a project by identifier.
     */
    void delete(Long id);

    /**
     * Returns the active project context.
     */
    Project findActiveContext();

    /**
     * Sets the active project context.
     */
    Project setActiveContext(Long id);

    /**
     * Clears any active project context.
     */
    void clearActiveContext();
}
