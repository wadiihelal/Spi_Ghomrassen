package com.promoteur.app.service.impl;

import com.promoteur.app.dto.ProjectRequest;
import com.promoteur.app.dto.response.ProjectResponse;
import com.promoteur.app.entity.Project;
import com.promoteur.app.exception.ResourceNotFoundException;
import com.promoteur.app.mapper.ProjectMapper;
import com.promoteur.app.repository.ProjectRepository;
import com.promoteur.app.service.AuditLogService;
import com.promoteur.app.service.MessageService;
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
    private final MessageService messageService;
    private final ProjectMapper projectMapper;

    @Override
    public Page<ProjectResponse> findAll(final Pageable pageable) {
        return this.projectRepository.findAll(pageable).map(this.projectMapper::toResponse);
    }

    @Override
    public ProjectResponse findById(final Long id) {
        return this.projectMapper.toResponse(this.entity(id));
    }

    /** Loads the persisted Project, for the write paths that need the entity itself. */
    private Project entity(final Long id) {
        return this.projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id " + id));
    }

    @Override
    public ProjectResponse create(final ProjectRequest request) {
        final Project project = new Project();
        this.map(project, request);
        if (this.projectRepository.findFirstByActiveContextTrue().isEmpty()) {
            project.setActiveContext(true);
        }
        final Project saved = this.projectRepository.save(project);
        this.auditLogService.create("PROJECT", saved.getId(), "CREATE",
                this.messageService.get("audit.project.created", saved.getName()));
        return this.projectMapper.toResponse(saved);
    }

    @Override
    public ProjectResponse update(final Long id, final ProjectRequest request) {
        final Project project = this.entity(id);
        this.map(project, request);
        final Project saved = this.projectRepository.save(project);
        this.auditLogService.create("PROJECT", saved.getId(), "UPDATE",
                this.messageService.get("audit.project.updated", saved.getName()));
        return this.projectMapper.toResponse(saved);
    }

    @Override
    public void delete(final Long id) {
        final Project project = this.entity(id);
        final String name = project.getName();
        final boolean wasActive = Boolean.TRUE.equals(project.getActiveContext());
        this.projectRepository.delete(project);
        if (wasActive) {
            this.projectRepository.findAll().stream().findFirst().ifPresent(next -> {
                next.setActiveContext(true);
                this.projectRepository.save(next);
            });
        }
        this.auditLogService.create("PROJECT", id, "DELETE",
                this.messageService.get("audit.project.deleted", name));
    }

    @Override
    public ProjectResponse findActiveContext() {
        return this.projectMapper.toResponse(this.projectRepository.findFirstByActiveContextTrue()
                .orElseThrow(() -> new ResourceNotFoundException("No active project context configured")));
    }

    @Override
    public ProjectResponse setActiveContext(final Long id) {
        final Project selected = this.entity(id);
        this.projectRepository.findAll().forEach(project -> {
            final boolean shouldBeActive = project.getId().equals(selected.getId());
            if (!Boolean.valueOf(shouldBeActive).equals(project.getActiveContext())) {
                project.setActiveContext(shouldBeActive);
                this.projectRepository.save(project);
            }
        });
        this.auditLogService.create("PROJECT_CONTEXT", selected.getId(), "UPDATE",
                this.messageService.get("audit.projectContext.set", selected.getName()));
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
        this.auditLogService.create("PROJECT_CONTEXT", 0L, "CLEAR",
                this.messageService.get("audit.projectContext.cleared"));
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
