import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { DropdownModule } from 'primeng/dropdown';
import { DialogModule } from 'primeng/dialog';
import { Apartment, Client, ClientAdvance, ClientPurchase, Project } from '../../shared/models/models';
import { ApiService } from '../../core/services/api.service';
import { UiService } from '../../core/services/ui.service';
import { ProjectContextService } from '../../core/services/project-context.service';
import { forkJoin } from 'rxjs';

@Component({
  selector: 'app-apartments',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, TableModule, CardModule, ButtonModule, InputTextModule, InputNumberModule, DropdownModule, DialogModule],
  templateUrl: './apartments.component.html',
  styleUrl: './apartments.component.css'
})
export class ApartmentsComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly ui = inject(UiService);
  private readonly projectContext = inject(ProjectContextService);

  apartments: Apartment[] = [];
  projects: Project[] = [];
  clients: Client[] = [];
  purchases: ClientPurchase[] = [];
  advances: ClientAdvance[] = [];
  editingId: number | null = null;
  dialogVisible = false;
  bulkMode = true;
  selectedProjectId: number | null = null;
  filters = {
    projectId: null as number | null,
    search: ''
  };

  apartmentTypes = [
    { label: 'S+1', value: 'S+1' },
    { label: 'S+2', value: 'S+2' },
    { label: 'S+3', value: 'S+3' },
    { label: 'S+4', value: 'S+4' },
    { label: 'Autre', value: 'AUTRE' }
  ];

  get currentProjectName(): string {
    if (!this.selectedProjectId) return 'Aucun projet sélectionné';
    return this.projects.find((item) => item.id === this.selectedProjectId)?.name ?? 'Projet en cours';
  }

  get availableClients(): Client[] {
    if (!this.selectedProjectId) {
      return this.clients;
    }
    return this.clients.filter((client) => client.projectId === this.selectedProjectId);
  }

  form = this.fb.group({
    apartmentNumber: ['', [Validators.required]],
    apartmentType: ['S+2', [Validators.required]],
    totalSurface: [0, [Validators.required]],
    gardenSurface: [0],
    parkingCount: [''],
    cellarCount: [0],
    totalSalePrice: [0],
    detail: [''],
    projectId: [null as number | null, [Validators.required]],
    acquirerId: [null as number | null]
  });

  bulkForm = this.fb.group({
    blockCode: ['A', [Validators.required]],
    floorCount: [5, [Validators.required]],
    apartmentsPerFloor: [3, [Validators.required]],
    apartmentType: ['S+2', [Validators.required]],
    totalSurface: [0, [Validators.required]],
    gardenSurface: [0],
    parkingCount: [''],
    cellarCount: [0],
    totalSalePrice: [0],
    detail: [''],
    projectId: [null as number | null, [Validators.required]]
  });

  ngOnInit(): void {
    this.projectContext.selectedProjectId$.subscribe((projectId) => {
      this.selectedProjectId = projectId;
      this.filters.projectId = projectId;
      if (!this.editingId) {
        this.form.patchValue({ projectId });
        this.bulkForm.patchValue({ projectId });
        const currentAcquirerId = this.form.get('acquirerId')?.value;
        if (currentAcquirerId && !this.availableClients.some((item) => item.id === currentAcquirerId)) {
          this.form.patchValue({ acquirerId: null });
        }
      }
    });
    this.loadData();
  }

  loadData(): void {
    this.api.getApartments().subscribe({ next: (data) => (this.apartments = data) });
    this.api.getProjects().subscribe({ next: (data) => (this.projects = data) });
    this.api.getClients().subscribe({ next: (data) => (this.clients = data) });
    this.api.getPurchases().subscribe({ next: (data) => (this.purchases = data) });
    this.api.getAdvances().subscribe({ next: (data) => (this.advances = data) });
  }

  get filteredApartments(): Apartment[] {
    return this.apartments.filter((row) => {
      const term = this.filters.search.trim().toLowerCase();
      const matchSearch = !term || [row.apartmentNumber, row.apartmentType, row.detail, row.acquirerName, row.projectName]
        .some((value) => (value ?? '').toString().toLowerCase().includes(term));
      const projectId = row.projectId;
      const matchProject = !this.filters.projectId || projectId === this.filters.projectId;
      const matchSelectedProject = !this.selectedProjectId || projectId === this.selectedProjectId;
      return matchSearch && matchProject && matchSelectedProject;
    });
  }

  getProjectName(row: Apartment): string {
    return row.projectName ?? this.projects.find((item) => item.id === row.projectId)?.name ?? '-';
  }

  getClientName(row: Apartment): string {
    return row.acquirerName ?? this.clients.find((item) => item.id === row.acquirerId)?.fullName ?? '-';
  }

  getPricePerSquareMeter(row: Apartment): number {
    const surface = row.totalSurface ?? 0;
    const price = row.totalSalePrice ?? 0;
    if (!surface) return 0;
    return Math.round((price / surface) * 1000) / 1000;
  }

  getTotalPurchases(row: Apartment): number {
    const apartmentId = row.id;
    if (!apartmentId) return 0;
    return this.purchases
      .filter((item) => item.apartmentId === apartmentId)
      .reduce((sum, item) => sum + (item.totalAmount ?? 0), 0);
  }

  getTotalAdvances(row: Apartment): number {
    const apartmentId = row.id;
    if (!apartmentId) return 0;
    return this.advances
      .filter((item) => item.apartmentId === apartmentId)
      .reduce((sum, item) => sum + (item.amount ?? 0), 0);
  }

  getTotalCollected(row: Apartment): number {
    const apartmentId = row.id;
    if (!apartmentId) return 0;
    return this.purchases
      .filter((item) => item.apartmentId === apartmentId)
      .reduce((sum, item) => sum + (item.collectedAmount ?? ((item.paidAmount ?? 0) + (item.advanceAmount ?? 0))), 0);
  }

  getRemainingToCollect(row: Apartment): number {
    const apartmentId = row.id;
    if (!apartmentId) return 0;
    return this.purchases
      .filter((item) => item.apartmentId === apartmentId)
      .reduce((sum, item) => sum + (item.remainingAmount ?? Math.max(0, (item.totalAmount ?? 0) - ((item.paidAmount ?? 0) + (item.advanceAmount ?? 0)))), 0);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.ui.info('Formulaire incomplet', 'Merci de remplir les champs obligatoires de l’appartement.');
      return;
    }

    const payload = this.form.getRawValue() as Apartment;
    const request$ = this.editingId ? this.api.updateApartment(this.editingId, payload) : this.api.createApartment(payload);
    request$.subscribe({
      next: () => {
        this.ui.success(this.editingId ? 'Appartement modifié' : 'Appartement ajouté', 'L’inventaire a été mis à jour.');
        this.resetForm();
        this.loadData();
      }
    });
  }

  submitBulk(): void {
    if (this.bulkForm.invalid) {
      this.bulkForm.markAllAsTouched();
      this.ui.info('Formulaire incomplet', 'Merci de remplir les champs obligatoires de génération du bloc.');
      return;
    }

    const payload = this.bulkForm.getRawValue();
    const blockCode = (payload.blockCode ?? '').trim().toUpperCase();
    const floorCount = Number(payload.floorCount) || 0;
    const apartmentsPerFloor = Number(payload.apartmentsPerFloor) || 0;

    if (!blockCode || floorCount <= 0 || apartmentsPerFloor <= 0) {
      this.ui.info('Données invalides', 'Merci de vérifier le bloc, le nombre d’étages et le nombre d’appartements.');
      return;
    }

    const requests = [];
    for (let floor = 0; floor < floorCount; floor += 1) {
      for (let apartmentIndex = 1; apartmentIndex <= apartmentsPerFloor; apartmentIndex += 1) {
        const apartmentNumber = `${blockCode}${floor}${apartmentIndex}`;
        requests.push(
          this.api.createApartment({
            apartmentNumber,
            apartmentType: payload.apartmentType ?? 'S+2',
            totalSurface: payload.totalSurface ?? 0,
            gardenSurface: payload.gardenSurface ?? 0,
            parkingCount: payload.parkingCount ?? '',
            cellarCount: payload.cellarCount ?? 0,
            totalSalePrice: payload.totalSalePrice ?? 0,
            detail: payload.detail ?? '',
            projectId: payload.projectId ?? undefined,
            acquirerId: null
          } as Apartment)
        );
      }
    }

    forkJoin(requests).subscribe({
      next: (created) => {
        this.ui.success('Bloc généré', `${created.length} appartements ont été créés pour le bloc ${blockCode}.`);
        this.resetBulkForm();
        this.loadData();
      }
    });
  }

  edit(row: Apartment): void {
    this.editingId = row.id ?? null;
    this.dialogVisible = true;
    this.form.patchValue({
      apartmentNumber: row.apartmentNumber,
      apartmentType: row.apartmentType,
      totalSurface: row.totalSurface,
      gardenSurface: row.gardenSurface ?? 0,
      parkingCount: row.parkingCount ?? '',
      cellarCount: row.cellarCount ?? 0,
      totalSalePrice: row.totalSalePrice ?? 0,
      detail: row.detail ?? '',
      projectId: this.selectedProjectId ?? row.projectId ?? null,
      acquirerId: row.acquirerId ?? null
    });
  }

  remove(row: Apartment): void {
    if (!row.id) return;
    this.ui.confirmDelete(`Supprimer l’appartement ${row.apartmentNumber} ?`, () => {
      this.api.deleteApartment(row.id!).subscribe({
        next: () => {
          this.ui.success('Appartement supprimé', 'L’appartement a été supprimé.');
          this.loadData();
          if (this.editingId === row.id) this.resetForm();
        }
      });
    });
  }

  resetForm(): void {
    this.editingId = null;
    this.dialogVisible = false;
    this.form.reset({
      apartmentNumber: '',
      apartmentType: 'S+2',
      totalSurface: 0,
      gardenSurface: 0,
      parkingCount: '',
      cellarCount: 0,
      totalSalePrice: 0,
      detail: '',
      projectId: this.selectedProjectId,
      acquirerId: null
    });
  }

  resetBulkForm(): void {
    this.bulkForm.reset({
      blockCode: 'A',
      floorCount: 5,
      apartmentsPerFloor: 3,
      apartmentType: 'S+2',
      totalSurface: 0,
      gardenSurface: 0,
      parkingCount: '',
      cellarCount: 0,
      totalSalePrice: 0,
      detail: '',
      projectId: this.selectedProjectId
    });
  }

  openCreateDialog(): void {
    this.resetForm();
    this.dialogVisible = true;
  }
}
