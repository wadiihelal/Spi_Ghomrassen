package com.promoteur.app.project;

import com.promoteur.app.audit.AuditLogService;
import com.promoteur.app.shared.MessageService;
import com.promoteur.app.shared.ReferenceGeneratorService;
import com.promoteur.app.shared.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final AuditLogService auditLogService;
    private final MessageService messageService;
    private final ProjectMapper projectMapper;
    private final ReferenceGeneratorService referenceGeneratorService;

    @Override
    @Transactional(readOnly = true)
    public Page<ProjectResponse> findAll(final Pageable pageable) {
        return this.projectRepository.findAll(pageable).map(this.projectMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectResponse findById(final Long id) {
        return this.projectMapper.toResponse(this.entity(id));
    }

    /**
     * Loads the persisted Project, for the write paths that need the entity itself.
     */
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
    @Transactional(readOnly = true)
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
        project.setCode(this.resolveCode(project, request));
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

    /**
     * Same rule as the other document references: a code typed by the promoter is kept exactly as
     * entered — the existing nomenclature ({@code SPI-GHOM-RES-01}) is never rewritten — and only
     * a blank one draws {@code PRJ-2026-00042} from the sequence. On an update, a field left blank
     * keeps the code the project already carries rather than issuing a new one.
     *
     * <p>The «&nbsp;is this code taken&nbsp;» check is passed to the generator rather than looked
     * up by it, so {@code shared} keeps no dependency on this feature.</p>
     */
    private String resolveCode(final Project project, final ProjectRequest request) {
        if (StringUtils.hasText(request.getCode())) {
            return request.getCode().trim();
        }
        if (StringUtils.hasText(project.getCode())) {
            return project.getCode();
        }
        return this.referenceGeneratorService.nextProjectCode(request.getStartDate(),
                candidate -> this.projectRepository.findByCode(candidate).isPresent());
    }
}
