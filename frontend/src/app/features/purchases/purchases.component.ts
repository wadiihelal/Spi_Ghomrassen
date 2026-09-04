import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
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
import { DinarPipe } from '../../shared/pipes/dinar.pipe';
import { PercentSharePipe } from '../../shared/pipes/percent-share.pipe';
import { UiService } from '../../core/services/ui.service';
import { AttachmentsPanelComponent } from '../../shared/attachments/attachments-panel.component';
import { ProjectContextService } from '../../core/services/project-context.service';
import { LazyTable } from '../../core/services/lazy-table';
import { ScheduleEditorComponent } from '../../shared/schedule/schedule-editor.component';
import {
  Apartment,
  Client,
  ClientAdvance,
  ClientPurchase,
  DashboardSummary,
  ListFilter,
  Project,
  PurchasePaymentStatus
} from '../../shared/models/models';

@Component({
  selector: 'app-purchases',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, TableModule, CardModule, ButtonModule, InputTextModule, InputNumberModule, DropdownModule, DialogModule, TagModule, AttachmentsPanelComponent, DinarPipe, PercentSharePipe, ScheduleEditorComponent],
  templateUrl: './purchases.component.html',
  styleUrl: './purchases.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PurchasesComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly ui = inject(UiService);
  private readonly projectContext = inject(ProjectContextService);
  private readonly destroyRef = inject(DestroyRef);

  /** One page of contracts, filtered and counted by the server (PERF-02). */
  readonly table = new LazyTable<ClientPurchase>(
    (query) => this.api.getPurchases(this.serverFilter, query),
    this.destroyRef
  );

  /** Contracts of the selected project, used to tell which apartments are still free. */
  readonly projectPurchases = signal<ClientPurchase[]>([]);
  readonly apartments = signal<Apartment[]>([]);
  /** Advances on the apartment currently chosen in the form, loaded on demand. */
  readonly selectedApartmentAdvances = signal<ClientAdvance[]>([]);
  /** Contract whose payment schedule is open in the dialog, and its total. */
  readonly scheduleForPurchase = signal<ClientPurchase | null>(null);
  scheduleDialogVisible = false;

  /** Scope-wide aggregate behind the KPI strip. */
  readonly summary = signal<DashboardSummary | undefined>(undefined);
  /** Contracts fully collected in scope, counted by the server. */
  readonly paidCount = signal(0);
  readonly clients = signal<Client[]>([]);
  readonly projects = signal<Project[]>([]);
  editingId: number | null = null;
  dialogVisible = false;
  readonly selectedProjectId = signal<number | null>(null);
  filters = {
    search: '',
    clientId: null as number | null,
    projectId: null as number | null,
    paymentStatus: '' as '' | PurchasePaymentStatus,
    dateFrom: '',
    dateTo: ''
  };

  paymentStatusOptions: { label: string; value: PurchasePaymentStatus }[] = [
    { label: 'Non soldé', value: 'UNPAID' },
    { label: 'Partiel', value: 'PARTIALLY_PAID' },
    { label: 'Soldé', value: 'PAID' }
  ];

  getClientName(clientId?: number | null): string {
    return this.clients().find((item) => item.id === clientId)?.fullName ?? '-';
  }

  getProjectName(projectId?: number | null): string {
    return this.projects().find((item) => item.id === projectId)?.name ?? '-';
  }

  /** Memoised: the header's project name, recomputed only when it changes. */
  readonly currentProjectName = computed(() => {
    if (!this.selectedProjectId()) return 'Aucun projet sélectionné';
    return this.projects().find((item) => item.id === this.selectedProjectId())?.name ?? 'Projet en cours';
  });

  get selectedFormProjectId(): number | null {
    return this.selectedProjectId() ?? this.form.get('projectId')?.value ?? null;
  }

  get selectedApartmentId(): number | null {
    return this.form.get('apartmentId')?.value ?? null;
  }

  get projectClients(): Client[] {
    if (!this.selectedProjectId()) {
      return this.clients();
    }
    return this.clients().filter((client) => client.projectId === this.selectedProjectId());
  }

  get availableApartments(): Apartment[] {
    const projectId = this.selectedFormProjectId;
    const clientId = this.form.get('clientId')?.value;
    const currentApartmentId = this.selectedApartmentId;

    return this.apartments().filter((apartment) => {
      const apartmentProjectId = apartment.projectId;
      if (projectId && apartmentProjectId !== projectId) {
        return false;
      }

      const apartmentPurchase = this.projectPurchases().find(
        (purchase) => purchase.apartmentId === apartment.id && purchase.id !== this.editingId
      );
      if (apartmentPurchase) {
        return false;
      }

      if (currentApartmentId && apartment.id === currentApartmentId) {
        return true;
      }

      const acquirerId = apartment.acquirerId ?? null;
      if (!clientId) {
        return true;
      }

      return !acquirerId || acquirerId === clientId;
    });
  }

  get currentAdvanceAmount(): number {
    if (!this.selectedApartmentId) {
      return 0;
    }

    return this.selectedApartmentAdvances().reduce((sum, advance) => sum + (advance.amount ?? 0), 0);
  }

  get currentDirectPaidAmount(): number {
    return Number(this.form.get('paidAmount')?.value ?? 0);
  }

  get currentDeclaredAmount(): number {
    return Number(this.form.get('totalAmount')?.value ?? 0);
  }

  get currentCollectedAmount(): number {
    return this.currentDirectPaidAmount + this.currentAdvanceAmount;
  }

  get currentRemainingAmount(): number {
    return Math.max(0, this.currentDeclaredAmount - this.currentCollectedAmount);
  }

  get currentCompletionPercentage(): number {
    if (!this.currentDeclaredAmount) {
      return 0;
    }

    return Math.min(100, Math.round((this.currentCollectedAmount / this.currentDeclaredAmount) * 1000) / 10);
  }

  get currentPaymentStatus(): PurchasePaymentStatus {
    if (this.currentCollectedAmount <= 0) {
      return 'UNPAID';
    }
    if (this.currentCollectedAmount >= this.currentDeclaredAmount) {
      return 'PAID';
    }
    return 'PARTIALLY_PAID';
  }

  get isCurrentPurchaseOverLimit(): boolean {
    return this.currentDeclaredAmount > 0 && this.currentCollectedAmount > this.currentDeclaredAmount;
  }

  /**
   * The KPI strip describes every contract in scope, not just the page on screen, so the
   * figures come from the backend's aggregate rather than from the loaded rows (PERF-02).
   */
  get filteredPurchaseCount(): number {
    return this.table.totalRecords();
  }

  get filteredDeclaredTotal(): number {
    return this.summary()?.totalPurchases ?? 0;
  }

  get filteredCollectedTotal(): number {
    return this.summary()?.totalAdvances ?? 0;
  }

  get filteredRemainingTotal(): number {
    return this.summary()?.totalRemainingFromClients ?? 0;
  }

  get filteredPaidCount(): number {
    return this.paidCount();
  }

  form = this.fb.group({
    reference: ['', [Validators.required]],
    purchaseDate: ['', [Validators.required]],
    contractDate: [''],
    totalAmount: [0, [Validators.required]],
    paidAmount: [0],
    assetDescription: ['', [Validators.required]],
    notes: [''],
    clientId: [null as number | null, [Validators.required]],
    apartmentId: [null as number | null, [Validators.required]],
    projectId: [null as number | null, [Validators.required]]
  });

  ngOnInit(): void {
    this.projectContext.scope$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((projectId) => {
      this.selectedProjectId.set(projectId);
      this.filters.projectId = projectId;
      // The table and every project-scoped lookup must follow the header (PERF-02): the
      // first page is fetched before the context arrives, and the user can switch project.
      this.table.onFilterChange();
      this.loadData();
      if (!this.editingId) {
        this.form.patchValue({ projectId });
        const currentClientId = this.form.get('clientId')?.value;
        if (currentClientId && !this.projectClients.some((item) => item.id === currentClientId)) {
          this.form.patchValue({ clientId: null });
        }
      }
    });
    this.form.get('clientId')?.valueChanges.subscribe((clientId) => this.syncApartmentToClient(clientId));
    this.form.get('apartmentId')?.valueChanges.subscribe((apartmentId) => this.syncClientToApartment(apartmentId));
  }

  /** Filters sent to the server; the header's project always narrows the list. */
  private get serverFilter(): ListFilter {
    return {
      projectId: this.selectedProjectId() ?? this.filters.projectId,
      clientId: this.filters.clientId,
      paymentStatus: this.filters.paymentStatus || null,
      dateFrom: this.filters.dateFrom,
      dateTo: this.filters.dateTo,
      search: this.filters.search
    };
  }

  loadData(): void {
    // Form lookups, scoped to the selected project and bounded: the apartment dropdown and the
    // "already sold" check. Never the table's own rows, which page server-side.
    this.api.getApartmentOptions(this.selectedProjectId())
      .subscribe({ next: (data) => this.apartments.set(data) });
    this.api.getPurchaseOptions(this.selectedProjectId())
      .subscribe({ next: (data) => this.projectPurchases.set(data) });
    this.api.getDashboardSummary({ projectId: this.selectedProjectId() })
      .subscribe({ next: (data) => this.summary.set(data) });
    // One page of size 1 is enough: only the total is read.
    this.api.getPurchases({ projectId: this.selectedProjectId(), paymentStatus: 'PAID' }, { page: 0, size: 1 })
      .subscribe({ next: (data) => this.paidCount.set(data.totalElements) });
    this.api.getClients().subscribe({ next: (data) => this.clients.set(data) });
    this.api.getProjects().subscribe({ next: (data) => this.projects.set(data) });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.ui.info('Formulaire incomplet', 'Merci de remplir les champs obligatoires de l’achat client.');
      return;
    }
    if (this.isCurrentPurchaseOverLimit) {
      this.ui.info('Montant dépassé', 'Le total encaissé ne peut pas dépasser le montant déclaré de l’achat.');
      return;
    }
    const payload = {
      ...this.form.getRawValue(),
      projectId: this.selectedFormProjectId
    } as ClientPurchase;
    const request$ = this.editingId
      ? this.api.updatePurchase(this.editingId, payload)
      : this.api.createPurchase(payload);

    request$.subscribe({
      next: () => {
        this.ui.success(this.editingId ? 'Achat modifié' : 'Achat ajouté', 'L’achat client a été enregistré avec succès.');
        this.resetForm();
        this.table.reload();
        this.loadData();
      }
    });
  }

  edit(purchase: ClientPurchase): void {
    this.editingId = purchase.id ?? null;
    this.dialogVisible = true;
    this.form.patchValue({
      reference: purchase.reference ?? '',
      purchaseDate: purchase.purchaseDate ?? '',
      contractDate: purchase.contractDate ?? '',
      totalAmount: purchase.totalAmount,
      paidAmount: purchase.paidAmount ?? 0,
      assetDescription: purchase.assetDescription,
      notes: purchase.notes ?? '',
      clientId: purchase.clientId ?? null,
      apartmentId: purchase.apartmentId ?? null,
      projectId: this.selectedProjectId() ?? purchase.projectId ?? null
    });
  }

  /** Opens the payment schedule of one contract (UX-03). */
  openSchedule(row: ClientPurchase): void {
    this.scheduleForPurchase.set(row);
    this.scheduleDialogVisible = true;
  }

  closeSchedule(): void {
    this.scheduleDialogVisible = false;
    this.scheduleForPurchase.set(null);
  }

  /** A plan change moves the collected/remaining figures, so the page reloads. */
  onScheduleChanged(): void {
    this.table.reload();
  }

  remove(row: ClientPurchase): void {
    if (!row.id) return;
    this.ui.confirmDelete(`Supprimer l’achat client ${row.reference} ?`, () => {
      this.api.deletePurchase(row.id!).subscribe({
        next: () => {
          this.ui.success('Achat supprimé', 'L’achat client a été supprimé.');
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
    this.form.reset({
      reference: '',
      purchaseDate: '',
      contractDate: '',
      totalAmount: 0,
      paidAmount: 0,
      assetDescription: '',
      notes: '',
      clientId: null,
      apartmentId: null,
      projectId: this.selectedProjectId()
    });
  }

  openCreateDialog(): void {
    this.resetForm();
    this.dialogVisible = true;
  }

  resetFilters(): void {
    this.filters = {
      search: '',
      clientId: null,
      projectId: this.selectedProjectId(),
      paymentStatus: '',
      dateFrom: '',
      dateTo: ''
    };
  }

  getApartmentName(apartmentId?: number | null): string {
    return this.apartments().find((item) => item.id === apartmentId)?.apartmentNumber ?? '-';
  }

  getPurchaseCollectedAmount(purchase: ClientPurchase): number {
    return purchase.collectedAmount ?? ((purchase.paidAmount ?? 0) + (purchase.advanceAmount ?? 0));
  }

  getPurchaseAdvanceAmount(purchase: ClientPurchase): number {
    return purchase.advanceAmount ?? 0;
  }

  getPurchaseDirectPaidAmount(purchase: ClientPurchase): number {
    return purchase.paidAmount ?? 0;
  }

  getPurchaseRemainingAmount(purchase: ClientPurchase): number {
    return purchase.remainingAmount ?? Math.max(0, (purchase.totalAmount ?? 0) - this.getPurchaseCollectedAmount(purchase));
  }

  getPurchaseCompletionPercentage(purchase: ClientPurchase): number {
    if (purchase.completionPercentage !== undefined && purchase.completionPercentage !== null) {
      return purchase.completionPercentage;
    }

    const totalAmount = purchase.totalAmount ?? 0;
    if (!totalAmount) {
      return 0;
    }

    return Math.min(100, Math.round((this.getPurchaseCollectedAmount(purchase) / totalAmount) * 1000) / 10);
  }

  getPaymentStatusLabel(status?: PurchasePaymentStatus): string {
    switch (status) {
      case 'PAID':
        return 'Soldé';
      case 'PARTIALLY_PAID':
        return 'Partiel';
      default:
        return 'Non soldé';
    }
  }

  getPaymentStatusSeverity(status?: PurchasePaymentStatus): 'success' | 'warning' | 'danger' | 'secondary' {
    switch (status) {
      case 'PAID':
        return 'success';
      case 'PARTIALLY_PAID':
        return 'warning';
      default:
        return 'danger';
    }
  }

  private syncClientToApartment(apartmentId: number | null): void {
    const apartment = this.apartments().find((item) => item.id === apartmentId);
    if (!apartment) {
      return;
    }

    const apartmentClientId = apartment.acquirerId ?? null;
    if (apartmentClientId && this.form.get('clientId')?.value !== apartmentClientId) {
      this.form.patchValue({ clientId: apartmentClientId }, { emitEvent: false });
    }

    const currentDescription = `${this.form.get('assetDescription')?.value ?? ''}`.trim();
    if (!currentDescription) {
      this.form.patchValue({
        assetDescription: apartment.detail?.trim() || `${apartment.apartmentType} ${apartment.apartmentNumber}`
      }, { emitEvent: false });
    }
  }

  private syncApartmentToClient(clientId: number | null): void {
    const apartmentId = this.selectedApartmentId;
    if (!apartmentId) {
      return;
    }

    const apartment = this.apartments().find((item) => item.id === apartmentId);
    if (!apartment) {
      return;
    }

    const apartmentClientId = apartment.acquirerId ?? null;
    if (apartmentClientId && clientId && apartmentClientId !== clientId) {
      this.form.patchValue({ apartmentId: null }, { emitEvent: false });
      this.ui.info('Appartement incompatible', 'Merci de choisir un appartement libre ou déjà affecté à ce client.');
    }
  }
}
