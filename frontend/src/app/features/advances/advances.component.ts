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
import { TagModule } from 'primeng/tag';
import { ApiService } from '../../core/services/api.service';
import { UiService } from '../../core/services/ui.service';
import { ProjectContextService } from '../../core/services/project-context.service';
import { LazyTable } from '../../core/services/lazy-table';
import {
  AmountByLabel,
  Apartment,
  Client,
  ClientAdvance,
  ClientPurchase,
  ListFilter,
  Project,
  PurchasePaymentStatus
} from '../../shared/models/models';

@Component({
  selector: 'app-advances',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, TableModule, CardModule, ButtonModule, InputTextModule, InputNumberModule, DropdownModule, DialogModule, TagModule],
  templateUrl: './advances.component.html',
  styleUrl: './advances.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AdvancesComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly ui = inject(UiService);
  private readonly projectContext = inject(ProjectContextService);
  private readonly destroyRef = inject(DestroyRef);
  /** The selected project as a stream, so the reaction can be released on destroy. */
  private readonly selectedProjectId$ = toObservable(this.projectContext.selectedProjectId);

  /** One page of advances, filtered and counted by the server (PERF-02). */
  readonly table = new LazyTable<ClientAdvance>(
    (query) => this.api.getAdvances(this.serverFilter, query),
    this.destroyRef
  );

  /** Advances on the apartment currently chosen in the form, loaded on demand. */
  readonly selectedApartmentAdvances = signal<ClientAdvance[]>([]);
  /** Advance totals per payment method for the selected project, aggregated by the backend. */
  readonly advanceTotalsByMethod = signal<AmountByLabel[]>([]);
  readonly apartments = signal<Apartment[]>([]);
  readonly purchases = signal<ClientPurchase[]>([]);
  readonly clients = signal<Client[]>([]);
  readonly projects = signal<Project[]>([]);
  editingId: number | null = null;
  dialogVisible = false;
  dialogProjectId: number | null = null;
  readonly selectedProjectId = signal<number | null>(null);
  filters = {
    search: '',
    clientId: null as number | null,
    projectId: null as number | null,
    paymentMethod: '',
    dateFrom: '',
    dateTo: ''
  };

  paymentMethods = [
    { label: 'Espèces', value: 'CASH' },
    { label: 'Virement bancaire', value: 'BANK_TRANSFER' },
    { label: 'Chèque', value: 'CHECK' },
    { label: 'Carte', value: 'CARD' },
    { label: 'Autre', value: 'OTHER' }
  ];

  getClientName(row: ClientAdvance): string {
    if (row.clientName) return row.clientName;
    const clientId = row.clientId;
    return this.clients().find((item) => item.id === clientId)?.fullName ?? '-';
  }

  getProjectName(row: ClientAdvance): string {
    if (row.projectName) return row.projectName;
    const projectId = row.projectId;
    return this.projects().find((item) => item.id === projectId)?.name ?? '-';
  }

  getApartmentLabel(apartment?: Apartment | null): string {
    if (!apartment) return '-';
    const clientName = apartment.acquirerName ?? 'Sans acquéreur';
    const projectName = apartment.projectName ?? 'Sans projet';
    return `${apartment.apartmentNumber} - ${clientName} - ${projectName}`;
  }

  getApartmentName(row: ClientAdvance): string {
    if (row.apartmentNumber) return row.apartmentNumber;
    const apartmentId = row.apartmentId;
    return this.apartments().find((item) => item.id === apartmentId)?.apartmentNumber ?? '-';
  }

  get selectedApartment(): Apartment | undefined {
    const apartmentId = this.form.get('apartmentId')?.value;
    return this.availableApartments.find((item) => item.id === apartmentId)
      ?? this.apartments().find((item) => item.id === apartmentId);
  }

  get selectedApartmentId(): number | null {
    return this.form.get('apartmentId')?.value ?? null;
  }

  get selectedApartmentPurchase(): ClientPurchase | undefined {
    return this.getPurchaseForApartment(this.selectedApartmentId);
  }

  get availableApartments(): Apartment[] {
    return this.apartments().filter((apartment) => {
      const activeProjectId = this.dialogProjectId ?? this.selectedProjectId();
      const matchesProject = !activeProjectId || apartment.projectId === activeProjectId;
      return matchesProject && !!apartment.acquirerId;
    });
  }

  /** Memoised: the header's project name, recomputed only when it changes. */
  readonly currentProjectName = computed(() => {
    const activeProjectId = this.dialogProjectId ?? this.selectedProjectId();
    if (!activeProjectId) return 'Aucun projet sélectionné';
    return this.projects().find((item) => item.id === activeProjectId)?.name ?? 'Projet en cours';
  });

  get projectClients(): Client[] {
    if (!this.selectedProjectId()) {
      return this.clients();
    }
    return this.clients().filter((client) => client.projectId === this.selectedProjectId());
  }

  getPaymentMethodLabel(value?: string | null): string {
    return this.paymentMethods.find((item) => item.value === value)?.label ?? (value || '-');
  }

  get selectedDeclaredAmount(): number {
    return this.selectedApartmentPurchase?.totalAmount ?? this.selectedApartment?.totalSalePrice ?? 0;
  }

  get selectedDirectPaidAmount(): number {
    return this.selectedApartmentPurchase?.paidAmount ?? 0;
  }

  get selectedExistingAdvancesAmount(): number {
    return this.selectedApartmentAdvances()
      .filter((advance) => advance.id !== this.editingId)
      .reduce((sum, advance) => sum + (advance.amount ?? 0), 0);
  }

  get currentAdvanceAmount(): number {
    return Number(this.form.get('amount')?.value ?? 0);
  }

  get selectedCollectedBeforeAmount(): number {
    return this.selectedDirectPaidAmount + this.selectedExistingAdvancesAmount;
  }

  get selectedProjectedCollectedAmount(): number {
    return this.selectedCollectedBeforeAmount + this.currentAdvanceAmount;
  }

  get selectedRemainingBeforeAmount(): number {
    return Math.max(0, this.selectedDeclaredAmount - this.selectedCollectedBeforeAmount);
  }

  get selectedProjectedRemainingAmount(): number {
    return Math.max(0, this.selectedDeclaredAmount - this.selectedProjectedCollectedAmount);
  }

  get selectedProjectedPercentage(): number {
    if (!this.selectedDeclaredAmount) {
      return 0;
    }

    return Math.min(100, Math.round((this.selectedProjectedCollectedAmount / this.selectedDeclaredAmount) * 1000) / 10);
  }

  get isCurrentAdvanceOverLimit(): boolean {
    return !!this.selectedApartmentPurchase && this.selectedProjectedCollectedAmount > this.selectedDeclaredAmount;
  }

  /**
   * The KPI strip describes every advance of the selected project, not just the page on screen.
   * The count comes from the page's own total, the amounts from the backend's aggregate — the
   * browser no longer holds the rows to reduce (PERF-02).
   */
  get filteredAdvanceCount(): number {
    return this.table.totalRecords();
  }

  get filteredAdvanceTotal(): number {
    return this.advanceTotalsByMethod().reduce((sum, row) => sum + (row.amount ?? 0), 0);
  }

  get filteredBankTransferTotal(): number {
    return this.advanceTotalsByMethod().find((row) => row.label === 'BANK_TRANSFER')?.amount ?? 0;
  }

  form = this.fb.group({
    reference: [''],
    advanceDate: ['', [Validators.required]],
    amount: [0, [Validators.required]],
    paymentMethod: ['BANK_TRANSFER', [Validators.required]],
    attachmentName: [''],
    attachmentUrl: [''],
    notes: [''],
    apartmentId: [null as number | null, [Validators.required]]
  });

  ngOnInit(): void {
    this.selectedProjectId$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((projectId) => {
      this.selectedProjectId.set(projectId);
      if (!this.dialogVisible || !this.editingId) {
        this.dialogProjectId = projectId;
      }
      this.filters.projectId = projectId;
      // The table and every project-scoped lookup must follow the header (PERF-02): the
      // first page is fetched before the context arrives, and the user can switch project.
      this.table.onFilterChange();
      this.loadData();
      if (!this.editingId) {
        const currentApartmentId = this.form.get('apartmentId')?.value;
        const apartmentStillVisible = this.availableApartments.some((item) => item.id === currentApartmentId);
        if (!apartmentStillVisible) {
          this.form.patchValue({ apartmentId: null });
        }
      }
    });
  }

  /** Filters sent to the server; the header's project always narrows the list. */
  private get serverFilter(): ListFilter {
    return {
      projectId: this.selectedProjectId() ?? this.filters.projectId,
      clientId: this.filters.clientId,
      paymentMethod: this.filters.paymentMethod || null,
      dateFrom: this.filters.dateFrom,
      dateTo: this.filters.dateTo,
      search: this.filters.search
    };
  }

  loadData(): void {
    // Form lookups, scoped to the selected project and bounded: the apartment dropdown and the
    // contract behind each advance. Never the table's own rows, which page server-side.
    this.api.getApartmentOptions(this.selectedProjectId())
      .subscribe({ next: (data) => this.apartments.set(data) });
    this.api.getPurchaseOptions(this.selectedProjectId())
      .subscribe({ next: (data) => this.purchases.set(data) });
    this.api.getAdvancesByPaymentMethod({ projectId: this.selectedProjectId() })
      .subscribe({ next: (data) => this.advanceTotalsByMethod.set(data) });
    this.api.getClients().subscribe({ next: (data) => this.clients.set(data) });
    this.api.getProjects().subscribe({ next: (data) => this.projects.set(data) });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.ui.info('Formulaire incomplet', 'Merci de remplir les champs obligatoires de l’acompte.');
      return;
    }
    if (this.isCurrentAdvanceOverLimit) {
      this.ui.info('Montant dépassé', 'Cet acompte dépasse le reste à encaisser pour l’achat de cet appartement.');
      return;
    }
    const payload = this.form.getRawValue() as ClientAdvance;
    const request$ = this.editingId
      ? this.api.updateAdvance(this.editingId, payload)
      : this.api.createAdvance(payload);

    request$.subscribe({
      next: () => {
        this.ui.success(this.editingId ? 'Acompte modifié' : 'Acompte ajouté', 'L’acompte a été enregistré avec succès.');
        this.resetForm();
        this.table.reload();
        this.loadData();
      }
    });
  }

  edit(advance: ClientAdvance): void {
    this.editingId = advance.id ?? null;
    this.dialogVisible = true;
    this.dialogProjectId = advance.projectId ?? this.selectedProjectId();
    this.form.patchValue({
      reference: advance.reference ?? '',
      advanceDate: advance.advanceDate ?? '',
      amount: advance.amount,
      paymentMethod: advance.paymentMethod ?? 'BANK_TRANSFER',
      attachmentName: advance.attachmentName ?? '',
      attachmentUrl: advance.attachmentUrl ?? '',
      notes: advance.notes ?? '',
      apartmentId: advance.apartmentId ?? null
    });
  }

  remove(row: ClientAdvance): void {
    if (!row.id) return;
    this.ui.confirmDelete(`Supprimer l’acompte ${row.reference} ?`, () => {
      this.api.deleteAdvance(row.id!).subscribe({
        next: () => {
          this.ui.success('Acompte supprimé', 'L’acompte a été supprimé.');
          this.table.reload();
        this.loadData();
          if (this.editingId === row.id) this.resetForm();
        }
      });
    });
  }

  resetForm(): void {
    this.editingId = null;
    this.dialogVisible = false;
    this.dialogProjectId = this.selectedProjectId();
    this.form.reset({
      reference: '',
      advanceDate: '',
      amount: 0,
      paymentMethod: 'BANK_TRANSFER',
      attachmentName: '',
      attachmentUrl: '',
      notes: '',
      apartmentId: null
    });
  }

  openCreateDialog(): void {
    this.resetForm();
    this.dialogProjectId = this.selectedProjectId();
    this.dialogVisible = true;
  }

  resetFilters(): void {
    this.filters = {
      search: '',
      clientId: null,
      projectId: this.selectedProjectId(),
      paymentMethod: '',
      dateFrom: '',
      dateTo: ''
    };
  }

  getPurchaseForApartment(apartmentId?: number | null): ClientPurchase | undefined {
    if (!apartmentId) {
      return undefined;
    }

    return this.purchases().find((purchase) => purchase.apartmentId === apartmentId);
  }

  getPurchaseForAdvance(advance: ClientAdvance): ClientPurchase | undefined {
    return this.getPurchaseForApartment(advance.apartmentId);
  }

  getPurchaseStatusLabel(status?: PurchasePaymentStatus): string {
    switch (status) {
      case 'PAID':
        return 'Soldé';
      case 'PARTIALLY_PAID':
        return 'Partiel';
      default:
        return 'Non soldé';
    }
  }

  getPurchaseStatusSeverity(status?: PurchasePaymentStatus): 'success' | 'warning' | 'danger' | 'secondary' {
    switch (status) {
      case 'PAID':
        return 'success';
      case 'PARTIALLY_PAID':
        return 'warning';
      default:
        return 'danger';
    }
  }

  getRemainingForAdvance(advance: ClientAdvance): number | null {
    const apartmentId = advance.apartmentId;
    const purchase = this.getPurchaseForApartment(apartmentId);
    if (!purchase) {
      return null;
    }

    // The contract already carries what is left to collect, derived server-side (PERF-02).
    return purchase.remainingAmount ?? Math.max(0, (purchase.totalAmount ?? 0) - (purchase.collectedAmount ?? 0));
  }

  /** Advances already recorded on the apartment chosen in the form, fetched when it changes. */
  private loadSelectedApartmentAdvances(apartmentId: number | null): void {
    if (!apartmentId) {
      this.selectedApartmentAdvances.set([]);
      return;
    }
    this.api.getAdvancesForApartment(apartmentId)
      .subscribe({ next: (data) => this.selectedApartmentAdvances.set(data) });
  }
}
