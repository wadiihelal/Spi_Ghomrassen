import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DialogModule } from 'primeng/dialog';
import { ApiService } from '../../core/services/api.service';
import { UiService } from '../../core/services/ui.service';
import { ProjectContextService } from '../../core/services/project-context.service';
import { Client, Project } from '../../shared/models/models';

@Component({
  selector: 'app-clients',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, TableModule, CardModule, ButtonModule, InputTextModule, DialogModule],
  templateUrl: './clients.component.html',
  styleUrl: './clients.component.css'
})
export class ClientsComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly ui = inject(UiService);
  private readonly projectContext = inject(ProjectContextService);

  clients: Client[] = [];
  projects: Project[] = [];
  editingId: number | null = null;
  dialogVisible = false;
  selectedProjectId: number | null = null;

  get currentProjectName(): string {
    if (!this.selectedProjectId) return 'Aucun projet sélectionné';
    return this.projects.find((item) => item.id === this.selectedProjectId)?.name ?? 'Projet en cours';
  }

  form = this.fb.group({
    fullName: ['', [Validators.required]],
    phone: [''],
    email: [''],
    address: [''],
    cinOrFiscalId: [''],
    notes: [''],
    active: [true],
    projectId: [null as number | null, [Validators.required]]
  });

  ngOnInit(): void {
    this.projectContext.selectedProjectId$.subscribe((projectId) => {
      this.selectedProjectId = projectId;
      if (!this.editingId) {
        this.form.patchValue({ projectId });
      }
    });
    this.loadClients();
  }

  loadClients(): void {
    this.api.getClients().subscribe({
      next: (data) => (this.clients = data)
    });
    this.api.getProjects().subscribe({ next: (data) => (this.projects = data) });
  }

  get filteredClients(): Client[] {
    if (!this.selectedProjectId) {
      return this.clients;
    }
    return this.clients.filter((client) => client.projectId === this.selectedProjectId);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.ui.info('Formulaire incomplet', 'Merci de remplir les champs obligatoires du client.');
      return;
    }

    const payload = this.form.getRawValue() as Client;
    const request$ = this.editingId
      ? this.api.updateClient(this.editingId, payload)
      : this.api.createClient(payload);

    request$.subscribe({
      next: () => {
        this.ui.success(this.editingId ? 'Client modifié' : 'Client ajouté', 'Les informations du client ont été enregistrées.');
        this.resetForm();
        this.loadClients();
      }
    });
  }

  edit(client: Client): void {
    this.editingId = client.id ?? null;
    this.dialogVisible = true;
    this.form.patchValue({
      fullName: client.fullName,
      phone: client.phone ?? '',
      email: client.email ?? '',
      address: client.address ?? '',
      cinOrFiscalId: client.cinOrFiscalId ?? '',
      notes: client.notes ?? '',
      active: client.active ?? true,
      projectId: this.selectedProjectId ?? client.projectId ?? null
    });
  }

  remove(client: Client): void {
    if (!client.id) {
      return;
    }

    this.ui.confirmDelete(`Supprimer le client ${client.fullName} ?`, () => {
      this.api.deleteClient(client.id!).subscribe({
        next: () => {
          if (this.editingId === client.id) {
            this.resetForm();
          }
          this.ui.success('Client supprimé', 'Le client a été supprimé avec succès.');
          this.loadClients();
        }
      });
    });
  }

  resetForm(): void {
    this.editingId = null;
    this.dialogVisible = false;
    this.form.reset({
      fullName: '',
      phone: '',
      email: '',
      address: '',
      cinOrFiscalId: '',
      notes: '',
      active: true,
      projectId: this.selectedProjectId
    });
  }

  openCreateDialog(): void {
    this.resetForm();
    this.dialogVisible = true;
  }
}
