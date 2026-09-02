package com.promoteur.app.service.impl;

import com.promoteur.app.dto.ClientRequest;
import com.promoteur.app.entity.Client;
import com.promoteur.app.entity.Project;
import com.promoteur.app.exception.ResourceNotFoundException;
import com.promoteur.app.repository.ClientRepository;
import com.promoteur.app.repository.ProjectRepository;
import com.promoteur.app.service.AuditLogService;
import com.promoteur.app.service.ClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ClientServiceImpl implements ClientService {

    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final AuditLogService auditLogService;

    @Override
    public Page<Client> findAll(final Pageable pageable) {
        return this.clientRepository.findAll(pageable);
    }

    @Override
    public Client findById(final Long id) {
        return this.clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id " + id));
    }

    @Override
    public Client create(final ClientRequest request) {
        final Client client = new Client();
        this.map(client, request);
        final Client saved = this.clientRepository.save(client);
        this.auditLogService.create("CLIENT", saved.getId(), "CREATE", "Client " + saved.getFullName() + " cree.");
        return saved;
    }

    @Override
    public Client update(final Long id, final ClientRequest request) {
        final Client client = this.findById(id);
        this.map(client, request);
        final Client saved = this.clientRepository.save(client);
        this.auditLogService.create("CLIENT", saved.getId(), "UPDATE", "Client " + saved.getFullName() + " modifie.");
        return saved;
    }

    @Override
    public void delete(final Long id) {
        final Client client = this.findById(id);
        this.clientRepository.delete(client);
        this.auditLogService.create("CLIENT", id, "DELETE", "Client " + client.getFullName() + " supprime.");
    }

    private void map(final Client client, final ClientRequest request) {
        final Project project = this.projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id " + request.getProjectId()));

        client.setFullName(request.getFullName());
        client.setPhone(request.getPhone());
        client.setEmail(request.getEmail());
        client.setAddress(request.getAddress());
        client.setCinOrFiscalId(request.getCinOrFiscalId());
        client.setNotes(request.getNotes());
        client.setActive(request.getActive() != null ? request.getActive() : true);
        client.setProject(project);
    }
}
