import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { DropdownModule } from 'primeng/dropdown';
import { DialogModule } from 'primeng/dialog';
import { Apartment, Client, ListFilter, Project } from '../../shared/models/models';
import { ApiService } from '../../core/services/api.service';
import { DinarPipe } from '../../shared/pipes/dinar.pipe';
import { UiService } from '../../core/services/ui.service';
import { ProjectContextService } from '../../core/services/project-context.service';
import { LazyTable } from '../../core/services/lazy-table';
import { forkJoin } from 'rxjs';

@Component({
  selector: 'app-apartments',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, TableModule, CardModule, ButtonModule, InputTextModule, InputNumberModule, DropdownModule, DialogModule, DinarPipe],
  templateUrl: './apartments.component.html',
  styleUrl: './apartments.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApartmentsComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly ui = inject(UiService);
  private readonly projectContext = inject(ProjectContextService);
  private readonly destroyRef = inject(DestroyRef);
  /** The selected project as a stream, so the reaction can be released on destroy. */
  private readonly selectedProjectId$ = toObservable(this.projectContext.selectedProjectId);

  /** One page of apartments, filtered and counted by the server (PERF-02). */
  readonly table = new LazyTable<Apartment>(
    (query) => this.api.getApartments(this.serverFilter, query),
    this.destroyRef
  );
  readonly projects = signal<Project[]>([]);
  readonly clients = signal<Client[]>([]);
  editingId: number | null = null;
  dialogVisible = false;
  bulkMode = true;
  readonly selectedProjectId = signal<number | null>(null);
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

  /** Memoised: the header's project name, recomputed only when it changes. */
  readonly currentProjectName = computed(() => {
    if (!this.selectedProjectId()) return 'Aucun projet sélectionné';
    return this.projects().find((item) => item.id === this.selectedProjectId())?.name ?? 'Projet en cours';
  });

  get availableClients(): Client[] {
    if (!this.selectedProjectId()) {
      return this.clients();
    }
    return this.clients().filter((client) => client.projectId === this.selectedProjectId());
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
    this.selectedProjectId$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((projectId) => {
      this.selectedProjectId.set(projectId);
      this.filters.projectId = projectId;
      // The table and every project-scoped lookup must follow the header (PERF-02): the
      // first page is fetched before the context arrives, and the user can switch project.
      this.table.onFilterChange();
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

  /** Filters sent to the server; the header's project always narrows the list. */
  private get serverFilter(): ListFilter {
    return {
      projectId: this.selectedProjectId() ?? this.filters.projectId,
      search: this.filters.search
    };
  }

  loadData(): void {
    this.api.getProjects().subscribe({ next: (data) => this.projects.set(data) });
    this.api.getClients().subscribe({ next: (data) => this.clients.set(data) });
  }

  getProjectName(row: Apartment): string {
    return row.projectName ?? this.projects().find((item) => item.id === row.projectId)?.name ?? '-';
  }

  getClientName(row: Apartment): string {
    return row.acquirerName ?? this.clients().find((item) => item.id === row.acquirerId)?.fullName ?? '-';
  }

  getPricePerSquareMeter(row: Apartment): number {
    const surface = row.totalSurface ?? 0;
    const price = row.totalSalePrice ?? 0;
    if (!surface) return 0;
    return Math.round((price / surface) * 1000) / 1000;
  }

  /**
   * The four figures below are derived by the backend and carried on the row (PERF-02). They
   * used to be reduced from the full purchase and advance lists held in the browser.
   */
  getTotalPurchases(row: Apartment): number {
    return row.totalPurchases ?? 0;
  }

  getTotalAdvances(row: Apartment): number {
    return row.totalAdvances ?? 0;
  }

  getTotalCollected(row: Apartment): number {
    return row.totalCollected ?? 0;
  }

  getRemainingToCollect(row: Apartment): number {
    return row.remainingToCollect ?? 0;
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
        this.table.reload();
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
        this.table.reload();
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
      projectId: this.selectedProjectId() ?? row.projectId ?? null,
      acquirerId: row.acquirerId ?? null
    });
  }

  remove(row: Apartment): void {
    if (!row.id) return;
    this.ui.confirmDelete(`Supprimer l’appartement ${row.apartmentNumber} ?`, () => {
      this.api.deleteApartment(row.id!).subscribe({
        next: () => {
          this.ui.success('Appartement supprimé', 'L’appartement a été supprimé.');
          this.table.reload();
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
      projectId: this.selectedProjectId(),
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
      projectId: this.selectedProjectId()
    });
  }

  openCreateDialog(): void {
    this.resetForm();
    this.dialogVisible = true;
  }
}
