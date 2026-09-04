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
import { ApiService } from '../../core/services/api.service';
import { UiService } from '../../core/services/ui.service';
import { AttachmentsPanelComponent } from '../../shared/attachments/attachments-panel.component';
import { ProjectContextService } from '../../core/services/project-context.service';
import { LazyTable } from '../../core/services/lazy-table';
import { Expense, ExpenseCategory, ListFilter, Project, Supplier, VatRateOption } from '../../shared/models/models';

/** Rate proposed when the supplier has none of its own. */
const FALLBACK_VAT_RATE = 0.19;

@Component({
  selector: 'app-expenses',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, TableModule, CardModule, ButtonModule, InputTextModule, InputNumberModule, DropdownModule, DialogModule, AttachmentsPanelComponent],
  templateUrl: './expenses.component.html',
  styleUrl: './expenses.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ExpensesComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly ui = inject(UiService);
  private readonly projectContext = inject(ProjectContextService);
  private readonly destroyRef = inject(DestroyRef);
  /** The selected project as a stream, so the reaction can be released on destroy. */
  private readonly selectedProjectId$ = toObservable(this.projectContext.selectedProjectId);

  /** One page of expenses, filtered and counted by the server (PERF-02). */
  readonly table = new LazyTable<Expense>(
    (query) => this.api.getExpenses(this.serverFilter, query),
    this.destroyRef
  );

  readonly categories = signal<ExpenseCategory[]>([]);
  readonly projects = signal<Project[]>([]);
  readonly suppliers = signal<Supplier[]>([]);
  editingId: number | null = null;
  expenseDialogVisible = false;
  categoryDialogVisible = false;
  readonly vatRates = signal<VatRateOption[]>([]);
  readonly selectedProjectId = signal<number | null>(null);
  filters = {
    search: '',
    categoryId: null as number | null,
    projectId: null as number | null,
    supplierId: null as number | null,
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

  form = this.fb.group({
    reference: [''],
    description: ['', [Validators.required]],
    expenseDate: ['', [Validators.required]],
    amountHt: [0, [Validators.required]],
    vatRate: [FALLBACK_VAT_RATE, [Validators.required]],
    paymentMethod: ['OTHER'],
    documentNumber: [''],
    notes: [''],
    categoryId: [null as number | null, [Validators.required]],
    projectId: [null as number | null, [Validators.required]],
    supplierId: [null as number | null]
  });

  categoryForm = this.fb.group({
    name: ['', [Validators.required]]
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
      }
    });
    this.loadReferenceData();
  }

  /** Filters sent to the server; the header's project always narrows the list. */
  private get serverFilter(): ListFilter {
    return {
      search: this.filters.search,
      categoryId: this.filters.categoryId,
      projectId: this.selectedProjectId() ?? this.filters.projectId,
      supplierId: this.filters.supplierId,
      dateFrom: this.filters.dateFrom,
      dateTo: this.filters.dateTo
    };
  }

  loadReferenceData(): void {
    this.api.getExpenseCategories().subscribe({ next: (data) => this.categories.set(data) });
    this.api.getProjects().subscribe({ next: (data) => this.projects.set(data) });
    this.api.getSuppliers().subscribe({ next: (data) => this.suppliers.set(data) });
    this.api.getVatRates().subscribe({ next: (data) => this.vatRates.set(data) });
  }

  getCategoryName(row: Expense): string {
    if (row.categoryName) return row.categoryName;
    const categoryId = row.categoryId;
    return this.categories().find((item) => item.id === categoryId)?.name ?? '-';
  }

  getProjectName(row: Expense): string {
    if (row.projectName) return row.projectName;
    const projectId = row.projectId;
    return this.projects().find((item) => item.id === projectId)?.name ?? '-';
  }

  /** Memoised: the header's project name, recomputed only when it changes. */
  readonly currentProjectName = computed(() => {
    if (!this.selectedProjectId()) return 'Aucun projet sélectionné';
    return this.projects().find((item) => item.id === this.selectedProjectId())?.name ?? 'Projet en cours';
  });

  getSupplierName(row: Expense): string {
    if (row.supplierName) return row.supplierName;
    const supplierId = row.supplierId;
    if (!supplierId) return '-';
    return this.suppliers().find((item) => item.id === supplierId)?.name ?? '-';
  }

  getPaymentMethodLabel(value?: string | null): string {
    return this.paymentMethods.find((item) => item.value === value)?.label ?? (value || '-');
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.ui.info('Formulaire incomplet', 'Merci de remplir les champs obligatoires de la dépense.');
      return;
    }

    const payload = this.form.getRawValue() as Expense;
    const request$ = this.editingId
      ? this.api.updateExpense(this.editingId, payload)
      : this.api.createExpense(payload);

    request$.subscribe({
      next: () => {
        this.ui.success(this.editingId ? 'Dépense modifiée' : 'Dépense ajoutée', 'La dépense a été enregistrée avec succès.');
        this.resetForm();
        this.table.reload();
      }
    });
  }

  edit(expense: Expense): void {
    this.editingId = expense.id ?? null;
    this.expenseDialogVisible = true;
    this.form.patchValue({
      reference: expense.reference ?? '',
      description: expense.description,
      expenseDate: expense.expenseDate ?? '',
      amountHt: expense.amountHt,
      vatRate: expense.vatRate ?? FALLBACK_VAT_RATE,
      paymentMethod: expense.paymentMethod ?? 'OTHER',
      documentNumber: expense.documentNumber ?? '',
      notes: expense.notes ?? '',
      categoryId: expense.categoryId ?? null,
      projectId: expense.projectId ?? null,
      supplierId: expense.supplierId ?? null
    });
    if (this.selectedProjectId()) {
      this.form.patchValue({ projectId: this.selectedProjectId() });
    }
  }

  remove(row: Expense): void {
    if (!row.id) return;
    this.ui.confirmDelete(`Supprimer la dépense ${row.reference || row.description} ?`, () => {
      this.api.deleteExpense(row.id!).subscribe({
        next: () => {
          this.ui.success('Dépense supprimée', 'La dépense a été supprimée.');
          this.table.reload();
          if (this.editingId === row.id) this.resetForm();
        }
      });
    });
  }

  resetForm(): void {
    this.editingId = null;
    this.expenseDialogVisible = false;
    this.form.reset({
      reference: '',
      amountHt: 0,
      vatRate: FALLBACK_VAT_RATE,
      paymentMethod: 'OTHER',
      documentNumber: '',
      notes: '',
      supplierId: null,
      categoryId: null,
      projectId: this.selectedProjectId(),
      description: '',
      expenseDate: ''
    });
  }

  openCreateDialog(): void {
    this.resetForm();
    this.expenseDialogVisible = true;
  }

  /**
   * Preview only. The backend derives the VAT and gross amounts from the net amount and the
   * rate (CALC-01), so these values are shown as read-only text and never sent.
   */
  get computedVatAmount(): number {
    const amountHt = Number(this.form.controls.amountHt.value) || 0;
    return this.roundAmount(amountHt * (Number(this.form.controls.vatRate.value) || 0));
  }

  get computedAmountTtc(): number {
    return this.roundAmount((Number(this.form.controls.amountHt.value) || 0) + this.computedVatAmount);
  }

  /** Proposes the rate this supplier usually invoices, falling back to 19 %. */
  onSupplierChange(supplierId: number | null): void {
    const supplier = this.suppliers().find((item) => item.id === supplierId);
    this.form.patchValue({ vatRate: supplier?.defaultVatRate ?? FALLBACK_VAT_RATE });
  }

  private roundAmount(value: number): number {
    return Math.round((value + Number.EPSILON) * 1000) / 1000;
  }

  openCategoryDialog(): void {
    this.categoryDialogVisible = true;
  }

  closeCategoryDialog(): void {
    this.categoryDialogVisible = false;
    this.categoryForm.reset({ name: '' });
  }

  submitCategory(): void {
    if (this.categoryForm.invalid) {
      this.categoryForm.markAllAsTouched();
      this.ui.info('Formulaire incomplet', 'Merci de remplir les champs obligatoires de la catégorie.');
      return;
    }

    this.api.createExpenseCategory(this.categoryForm.getRawValue() as ExpenseCategory).subscribe({
      next: (created) => {
        this.ui.success('Catégorie ajoutée', 'La catégorie de dépense a été enregistrée.');
        this.table.reload();
        this.form.patchValue({ categoryId: created.id });
        this.closeCategoryDialog();
      }
    });
  }
}
