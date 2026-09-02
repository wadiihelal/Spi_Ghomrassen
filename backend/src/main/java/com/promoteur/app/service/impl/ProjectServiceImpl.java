package com.promoteur.app.service.impl;

import com.promoteur.app.dto.ProjectRequest;
import com.promoteur.app.entity.Project;
import com.promoteur.app.exception.ResourceNotFoundException;
import com.promoteur.app.repository.ProjectRepository;
import com.promoteur.app.service.AuditLogService;
import com.promoteur.app.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final AuditLogService auditLogService;

    @Override
    public Page<Project> findAll(final Pageable pageable) {
        return this.projectRepository.findAll(pageable);
    }

    @Override
    public Project findById(final Long id) {
        return this.projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id " + id));
    }

    @Override
    public Project create(final ProjectRequest request) {
        final Project project = new Project();
        this.map(project, request);
        if (this.projectRepository.findFirstByActiveContextTrue().isEmpty()) {
            project.setActiveContext(true);
        }
        final Project saved = this.projectRepository.save(project);
        this.auditLogService.create("PROJECT", saved.getId(), "CREATE", "Projet " + saved.getName() + " cree.");
        return saved;
    }

    @Override
    public Project update(final Long id, final ProjectRequest request) {
        final Project project = this.findById(id);
        this.map(project, request);
        final Project saved = this.projectRepository.save(project);
        this.auditLogService.create("PROJECT", saved.getId(), "UPDATE", "Projet " + saved.getName() + " modifie.");
        return saved;
    }

    @Override
    public void delete(final Long id) {
        final Project project = this.findById(id);
        final boolean wasActive = Boolean.TRUE.equals(project.getActiveContext());
        this.projectRepository.delete(project);
        if (wasActive) {
            this.projectRepository.findAll().stream().findFirst().ifPresent(next -> {
                next.setActiveContext(true);
                this.projectRepository.save(next);
            });
        }
        this.auditLogService.create("PROJECT", id, "DELETE", "Projet " + project.getName() + " supprime.");
    }

    @Override
    public Project findActiveContext() {
        return this.projectRepository.findFirstByActiveContextTrue()
                .orElseThrow(() -> new ResourceNotFoundException("No active project context configured"));
    }

    @Override
    public Project setActiveContext(final Long id) {
        final Project selected = this.findById(id);
        this.projectRepository.findAll().forEach(project -> {
            final boolean shouldBeActive = project.getId().equals(selected.getId());
            if (!Boolean.valueOf(shouldBeActive).equals(project.getActiveContext())) {
                project.setActiveContext(shouldBeActive);
                this.projectRepository.save(project);
            }
        });
        this.auditLogService.create("PROJECT_CONTEXT", selected.getId(), "UPDATE", "Projet actif defini sur " + selected.getName() + ".");
        return this.findById(id);
    }

    @Override
    public void clearActiveContext() {
        this.projectRepository.findAll().forEach(project -> {
            if (Boolean.TRUE.equals(project.getActiveContext())) {
                project.setActiveContext(false);
                this.projectRepository.save(project);
            }
        });
        this.auditLogService.create("PROJECT_CONTEXT", 0L, "CLEAR", "Projet actif reinitialise.");
    }

    private void map(final Project project, final ProjectRequest request) {
        project.setCode(request.getCode());
        project.setName(request.getName());
        project.setLocation(request.getLocation());
        project.setDescription(request.getDescription());
        project.setStartDate(request.getStartDate());
        project.setExpectedEndDate(request.getExpectedEndDate());
        project.setBudget(request.getBudget());
        if (request.getStatus() != null) {
            project.setStatus(request.getStatus());
        }
    }
}
