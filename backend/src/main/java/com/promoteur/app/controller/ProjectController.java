package com.promoteur.app.controller;

import com.promoteur.app.dto.ProjectRequest;
import com.promoteur.app.dto.response.ProjectResponse;
import com.promoteur.app.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public Page<ProjectResponse> findAll(Pageable pageable) {
        return projectService.findAll(pageable);
    }

    @GetMapping("/{id}")
    public ProjectResponse findById(@PathVariable Long id) {
        return projectService.findById(id);
    }

    @PostMapping
    public ProjectResponse create(@Valid @RequestBody ProjectRequest request) {
        return projectService.create(request);
    }

    @PutMapping("/{id}")
    public ProjectResponse update(@PathVariable Long id, @Valid @RequestBody ProjectRequest request) {
        return projectService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        projectService.delete(id);
    }

    @GetMapping("/active-context")
    public ProjectResponse findActiveContext() {
        return projectService.findActiveContext();
    }

    @PutMapping("/active-context/{id}")
    public ProjectResponse setActiveContext(@PathVariable Long id) {
        return projectService.setActiveContext(id);
    }

    @DeleteMapping("/active-context")
    public void clearActiveContext() {
        projectService.clearActiveContext();
    }
}
