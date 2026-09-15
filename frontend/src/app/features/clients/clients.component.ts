import { ChangeDetectionStrategy, Component, ViewChild, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FieldLabels, missingFieldsMessage } from '../../core/services/required-fields';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Table, TableModule } from 'primeng/table';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DialogModule } from 'primeng/dialog';
import { ApiService } from '../../core/services/api.service';
import { UiService } from '../../core/services/ui.service';
import { ProjectContextService } from '../../core/services/project-context.service';
import { Client, Project } from '../../shared/models/models';


const CLIENT_FIELDS: FieldLabels = {
  fullName: 'Nom complet',
  projectId: 'Projet',
};

@Component({
  selector: 'app-clients',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, TableModule, CardModule, ButtonModule, InputTextModule, DialogModule],
  templateUrl: './clients.component.html',
  styleUrl: './clients.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ClientsComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly ui = inject(UiService);
  private readonly projectContext = inject(ProjectContextService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);

  /** Signals, so the view refreshes under OnPush when an HTTP response lands (PERF-04). */
  readonly clients = signal<Client[]>([]);
  readonly projects = signal<Project[]>([]);
  readonly selectedProjectId = signal<number | null>(null);
  editingId: number | null = null;
  dialogVisible = false;

  /** The clients of the selected project; the backend does the filtering. */
  readonly filteredClients = computed(() => this.clients());

  readonly currentProjectName = computed(() => {
    const projectId = this.selectedProjectId();
    if (!projectId) return 'Aucun projet sélectionné';
    return this.projects().find((item) => item.id === projectId)?.name ?? 'Projet en cours';
  });

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

  /** Text in the list's search box; prefilled by the global search's ?search= (UX-08). */
  searchTerm = '';
  @ViewChild('dt') private table?: Table;

  ngOnInit(): void {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => (this.searchTerm = params.get('search') ?? this.searchTerm));
    this.projectContext.scope$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((projectId) => {
      this.selectedProjectId.set(projectId);
      if (!this.editingId) {
        this.form.patchValue({ projectId });
      }
    });
    this.loadClients();
  }

  loadClients(): void {
    this.api.getClients(this.selectedProjectId()).subscribe({
      next: (data) => {
        this.clients.set(data);
        this.applySearchTerm();
      }
    });
    this.api.getProjects().subscribe({ next: (data) => (this.projects.set(data)) });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.ui.info('Formulaire incomplet',
        missingFieldsMessage(this.form, CLIENT_FIELDS, 'Merci de remplir les champs obligatoires du client.'));
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

  /** Address of the client's statement of account, opened as an ordinary link (UX-06). */
  statementUrl(row: Client): string {
    return row.id ? this.api.clientStatementUrl(row.id) : '';
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
      projectId: this.selectedProjectId() ?? client.projectId ?? null
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
      projectId: this.selectedProjectId()
    });
  }

  openCreateDialog(): void {
    this.resetForm();
    this.dialogVisible = true;
  }

  /** Pushes a prefilled search term into the table: PrimeNG filters only on the input event. */
  private applySearchTerm(): void {
    if (this.searchTerm) {
      // The table renders its rows on the next tick; filtering before that finds nothing.
      setTimeout(() => this.table?.filterGlobal(this.searchTerm, 'contains'));
    }
  }
}
