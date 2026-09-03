import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DropdownModule } from 'primeng/dropdown';
import { DialogModule } from 'primeng/dialog';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { UiService } from '../../core/services/ui.service';
import { Project } from '../../shared/models/models';

@Component({
  selector: 'app-projects',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, TableModule, CardModule, ButtonModule, InputTextModule, DropdownModule, DialogModule, RouterLink],
  templateUrl: './projects.component.html',
  styleUrl: './projects.component.css'
})
export class ProjectsComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly ui = inject(UiService);

  projects: Project[] = [];
  editingId: number | null = null;
  dialogVisible = false;
  filters = {
    search: '',
    status: '',
    location: ''
  };
  statuses = [
    { label: 'Planifié', value: 'PLANNED' },
    { label: 'En cours', value: 'IN_PROGRESS' },
    { label: 'Terminé', value: 'COMPLETED' },
    { label: 'Annulé', value: 'CANCELLED' }
  ];

  form = this.fb.group({
    code: ['', [Validators.required]],
    name: ['', [Validators.required]],
    location: [''],
    description: [''],
    startDate: [''],
    expectedEndDate: [''],
    budget: [null as number | null],
    status: ['PLANNED', [Validators.required]]
  });

  ngOnInit(): void { this.loadProjects(); }

  getStatusLabel(status?: string | null): string {
    return this.statuses.find(item => item.value === status)?.label ?? status ?? '-';
  }

  get filteredProjects(): Project[] {
    return this.projects.filter((project) => {
      const term = this.filters.search.trim().toLowerCase();
      const matchSearch = !term || [project.code, project.name, project.location, project.description]
        .some((value) => (value ?? '').toString().toLowerCase().includes(term));
      const matchStatus = !this.filters.status || project.status === this.filters.status;
      const matchLocation = !this.filters.location || (project.location ?? '').toLowerCase().includes(this.filters.location.toLowerCase());
      return matchSearch && matchStatus && matchLocation;
    });
  }

  loadProjects(): void {
    this.api.getProjects().subscribe({
      next: (data) => (this.projects = data)
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.ui.info('Formulaire incomplet', 'Merci de remplir les champs obligatoires du projet.');
      return;
    }
    const payload = this.form.getRawValue() as Project;
    const request$ = this.editingId ? this.api.updateProject(this.editingId, payload) : this.api.createProject(payload);
    request$.subscribe({
      next: () => {
        this.ui.success(this.editingId ? 'Projet modifié' : 'Projet ajouté', 'Le projet a été enregistré avec succès.');
        this.resetForm();
        this.loadProjects();
      }
    });
  }

  edit(project: Project): void {
    this.editingId = project.id ?? null;
    this.dialogVisible = true;
    this.form.patchValue({
      code: project.code ?? '',
      name: project.name,
      location: project.location ?? '',
      description: project.description ?? '',
      startDate: project.startDate ?? '',
      expectedEndDate: project.expectedEndDate ?? '',
      budget: project.budget ?? null,
      status: project.status ?? 'PLANNED'
    });
  }

  remove(project: Project): void {
    if (!project.id) {
      return;
    }
    this.ui.confirmDelete(`Supprimer le projet ${project.name} ?`, () => {
      this.api.deleteProject(project.id!).subscribe({
        next: () => {
          if (this.editingId === project.id) {
            this.resetForm();
          }
          this.ui.success('Projet supprimé', 'Le projet a été supprimé avec succès.');
          this.loadProjects();
        }
      });
    });
  }

  resetForm(): void {
    this.editingId = null;
    this.dialogVisible = false;
    this.form.reset({
      code: '',
      name: '',
      location: '',
      description: '',
      startDate: '',
      expectedEndDate: '',
      budget: null,
      status: 'PLANNED'
    });
  }

  openCreateDialog(): void {
    this.resetForm();
    this.dialogVisible = true;
  }
}
