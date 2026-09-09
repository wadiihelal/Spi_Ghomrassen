package com.promoteur.app.project;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@Tag(name = "Projets", description = "Résidences et projets, dont le projet actif de la console.")
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @Operation(summary = "Liste paginée et filtrée")
    @GetMapping
    public Page<ProjectResponse> findAll(Pageable pageable) {
        return projectService.findAll(pageable);
    }

    @Operation(summary = "Détail par identifiant")
    @GetMapping("/{id}")
    public ProjectResponse findById(@PathVariable Long id) {
        return projectService.findById(id);
    }

    @Operation(summary = "Création")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectRequest request) {
        ProjectResponse created = projectService.create(request);
        return ResponseEntity.created(locationOf(created.id())).body(created);
    }

    @Operation(summary = "Modification")
    @PutMapping("/{id}")
    public ProjectResponse update(@PathVariable Long id, @Valid @RequestBody ProjectRequest request) {
        return projectService.update(id, request);
    }

    @Operation(summary = "Suppression")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        projectService.delete(id);
    }

    @Operation(summary = "Projet actif de la console")
    @GetMapping("/active-context")
    public ProjectResponse findActiveContext() {
        return projectService.findActiveContext();
    }

    @Operation(summary = "Sélection du projet actif")
    @PutMapping("/active-context/{id}")
    public ProjectResponse setActiveContext(@PathVariable Long id) {
        return projectService.setActiveContext(id);
    }

    @Operation(summary = "Réinitialisation du projet actif")
    @DeleteMapping("/active-context")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearActiveContext() {
        projectService.clearActiveContext();
    }

    /**
     * Location of a freshly created resource, for the 201 response (API-02).
     */
    private URI locationOf(final Long id) {
        return ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
    }
}
