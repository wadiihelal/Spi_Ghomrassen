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
import { ListFilter, PayablesSummary, Project, SettlementStatus, Supplier, SupplierInvoice, VatRateOption } from '../../shared/models/models';

/** Settlement states offered in the filter bar (UX-04). */
const SETTLEMENT_OPTIONS = [
  { label: 'Tous les règlements', value: null },
  { label: 'À régler', value: 'UNPAID' },
  { label: 'Partiellement réglées', value: 'PARTIALLY_PAID' },
  { label: 'Réglées', value: 'PAID' },
  { label: 'En retard', value: 'OVERDUE' }
];

/** Rate proposed when the supplier has none of its own. */
const FALLBACK_VAT_RATE = 0.19;
import { ApiService } from '../../core/services/api.service';
import { DinarPipe } from '../../shared/pipes/dinar.pipe';
import { PercentSharePipe } from '../../shared/pipes/percent-share.pipe';
import { UiService } from '../../core/services/ui.service';
import { AttachmentsPanelComponent } from '../../shared/attachments/attachments-panel.component';
import { ProjectContextService } from '../../core/services/project-context.service';
import { LazyTable } from '../../core/services/lazy-table';
import { SupplierPaymentsComponent } from '../../shared/supplier-payments/supplier-payments.component';

@Component({
  selector: 'app-supplier-invoices',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, TableModule, CardModule, ButtonModule, InputTextModule, InputNumberModule, DropdownModule, DialogModule, TagModule, AttachmentsPanelComponent, DinarPipe, PercentSharePipe, SupplierPaymentsComponent],
  templateUrl: './supplier-invoices.component.html',
  styleUrl: './supplier-invoices.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SupplierInvoicesComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly ui = inject(UiService);
  private readonly projectContext = inject(ProjectContextService);
  private readonly destroyRef = inject(DestroyRef);

  /** One page of invoices, filtered and counted by the server (PERF-02). */
  readonly table = new LazyTable<SupplierInvoice>(
    (query) => this.api.getSupplierInvoices(this.serverFilter, query),
    this.destroyRef
  );

  readonly suppliers = signal<Supplier[]>([]);
  readonly vatRates = signal<VatRateOption[]>([]);
  readonly projects = signal<Project[]>([]);
  /** What is owed to suppliers in scope (UX-04). */
  readonly payables = signal<PayablesSummary | undefined>(undefined);
  readonly settlementOptions = SETTLEMENT_OPTIONS;
  /** Invoice whose payments are open in the dialog. */
  readonly paymentsForInvoice = signal<SupplierInvoice | null>(null);
  paymentsDialogVisible = false;
  editingId: number | null = null;
  dialogVisible = false;
  readonly selectedProjectId = signal<number | null>(null);
  filters = {
    projectId: null as number | null,
    supplierId: null as number | null,
    settlement: null as string | null,
    search: ''
  };

  /** Memoised: the header's project name, recomputed only when it changes. */
  readonly currentProjectName = computed(() => {
    if (!this.selectedProjectId()) return 'Aucun projet sélectionné';
    return this.projects().find((item) => item.id === this.selectedProjectId())?.name ?? 'Projet en cours';
  });

  form = this.fb.group({
    invoiceNumber: ['', [Validators.required]],
    invoiceDate: ['', [Validators.required]],
    dueDate: [''],
    amountHt: [0, [Validators.required]],
    vatRate: [FALLBACK_VAT_RATE, [Validators.required]],
    detail: [''],
    supplierId: [null as number | null, [Validators.required]],
    projectId: [null as number | null, [Validators.required]]
  });

  ngOnInit(): void {
    this.projectContext.scope$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((projectId) => {
      this.selectedProjectId.set(projectId);
      this.filters.projectId = projectId;
      // The table and every project-scoped lookup must follow the header (PERF-02): the
      // first page is fetched before the context arrives, and the user can switch project.
      this.table.onFilterChange();
      // The payables strip is scoped too, so it has to follow the header (UX-04).
      this.loadPayables();
      if (!this.editingId) {
        this.form.patchValue({ projectId });
      }
    });
    this.loadData();
  }

  /** Filters sent to the server; the header's project always narrows the list. */
  private get serverFilter(): ListFilter {
    return {
      projectId: this.selectedProjectId() ?? this.filters.projectId,
      supplierId: this.filters.supplierId,
      settlement: this.filters.settlement,
      search: this.filters.search
    };
  }

  loadData(): void {
    this.api.getSuppliers().subscribe({ next: (data) => this.suppliers.set(data) });
    this.api.getProjects().subscribe({ next: (data) => this.projects.set(data) });
    this.api.getVatRates().subscribe({ next: (data) => this.vatRates.set(data) });
  }

  /** What is owed to suppliers on the project in scope. */
  private loadPayables(): void {
    this.api.getPayablesSummary(this.selectedProjectId())
      .subscribe({ next: (data) => this.payables.set(data) });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.ui.info('Formulaire incomplet', 'Merci de remplir les champs obligatoires de la facture.');
      return;
    }

    const payload = this.form.getRawValue() as SupplierInvoice;
    const request$ = this.editingId ? this.api.updateSupplierInvoice(this.editingId, payload) : this.api.createSupplierInvoice(payload);
    request$.subscribe({
      next: () => {
        this.ui.success(this.editingId ? 'Facture modifiée' : 'Facture ajoutée', 'La facture fournisseur a été enregistrée.');
        this.resetForm();
        this.table.reload();
      }
    });
  }

  edit(row: SupplierInvoice): void {
    this.editingId = row.id ?? null;
    this.dialogVisible = true;
    this.form.patchValue({
      invoiceNumber: row.invoiceNumber,
      invoiceDate: row.invoiceDate,
      dueDate: row.dueDate ?? '',
      amountHt: row.amountHt,
      vatRate: row.vatRate ?? FALLBACK_VAT_RATE,
      detail: row.detail ?? '',
      supplierId: row.supplierId ?? null,
      projectId: this.selectedProjectId() ?? row.projectId ?? null
    });
  }

  /** Opens the payments of one invoice (UX-04). */
  openPayments(row: SupplierInvoice): void {
    this.paymentsForInvoice.set(row);
    this.paymentsDialogVisible = true;
  }

  closePayments(): void {
    this.paymentsDialogVisible = false;
    this.paymentsForInvoice.set(null);
  }

  /** A payment changes what is owed, so the table and the payables strip both reload. */
  onPaymentsChanged(): void {
    this.table.reload();
    this.loadPayables();
  }

  settlementLabel(status?: SettlementStatus): string {
    switch (status) {
      case 'PAID': return 'Réglée';
      case 'PARTIALLY_PAID': return 'Partielle';
      default: return 'À régler';
    }
  }

  settlementSeverity(row: SupplierInvoice): 'success' | 'warning' | 'danger' | 'secondary' {
    if (row.status === 'PAID') return 'success';
    if (row.overdue) return 'danger';
    if (row.status === 'PARTIALLY_PAID') return 'warning';
    return 'secondary';
  }

  remove(row: SupplierInvoice): void {
    if (!row.id) return;
    this.ui.confirmDelete(`Supprimer la facture ${row.invoiceNumber} ?`, () => {
      this.api.deleteSupplierInvoice(row.id!).subscribe({
        next: () => {
          this.ui.success('Facture supprimée', 'La facture fournisseur a été supprimée.');
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
      invoiceNumber: '',
      invoiceDate: '',
      dueDate: '',
      amountHt: 0,
      vatRate: FALLBACK_VAT_RATE,
      detail: '',
      supplierId: null,
      projectId: this.selectedProjectId()
    });
  }

  openCreateDialog(): void {
    this.resetForm();
    this.dialogVisible = true;
  }

  /**
   * Preview only. The backend derives the VAT and gross amounts from the net amount and the
   * rate (CALC-01), so these values are shown as read-only text and never sent.
   */
  get computedVatAmount(): number {
    const amountHt = Number(this.form.controls.amountHt.value) || 0;
    return this.round(amountHt * (Number(this.form.controls.vatRate.value) || 0));
  }

  get computedAmountTtc(): number {
    return this.round((Number(this.form.controls.amountHt.value) || 0) + this.computedVatAmount);
  }

  /** Proposes the rate this supplier usually invoices, falling back to 19 %. */
  onSupplierChange(supplierId: number | null): void {
    const supplier = this.suppliers().find((item) => item.id === supplierId);
    this.form.patchValue({ vatRate: supplier?.defaultVatRate ?? FALLBACK_VAT_RATE });
  }

  private round(value: number): number {
    return Math.round((value + Number.EPSILON) * 1000) / 1000;
  }
}
