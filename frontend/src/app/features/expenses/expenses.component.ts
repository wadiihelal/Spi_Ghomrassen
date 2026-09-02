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
import { ApiService } from '../../core/services/api.service';
import { UiService } from '../../core/services/ui.service';
import { ProjectContextService } from '../../core/services/project-context.service';
import { Expense, ExpenseCategory, Project, Supplier } from '../../shared/models/models';

// TODO CALC-01 : taux de TVA figé à 19 % côté navigateur. La Tunisie applique 0 %, 7 %,
// 13 % et 19 % ; la phase 2.2 déplace le calcul vers le backend et rend le taux saisissable.
const DEFAULT_VAT_RATE = 0.19;

@Component({
  selector: 'app-expenses',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, TableModule, CardModule, ButtonModule, InputTextModule, InputNumberModule, DropdownModule, DialogModule],
  templateUrl: './expenses.component.html',
  styleUrl: './expenses.component.css'
})
export class ExpensesComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly ui = inject(UiService);
  private readonly projectContext = inject(ProjectContextService);

  expenses: Expense[] = [];
  categories: ExpenseCategory[] = [];
  projects: Project[] = [];
  suppliers: Supplier[] = [];
  editingId: number | null = null;
  expenseDialogVisible = false;
  categoryDialogVisible = false;
  readonly vatRate = DEFAULT_VAT_RATE;
  selectedProjectId: number | null = null;
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
    vatAmount: [{ value: 0, disabled: true }, [Validators.required]],
    amountTtc: [{ value: 0, disabled: true }, [Validators.required]],
    paymentMethod: ['OTHER'],
    documentNumber: [''],
    attachmentName: [''],
    attachmentUrl: [''],
    notes: [''],
    categoryId: [null as number | null, [Validators.required]],
    projectId: [null as number | null, [Validators.required]],
    supplierId: [null as number | null]
  });

  categoryForm = this.fb.group({
    name: ['', [Validators.required]]
  });

  ngOnInit(): void {
    this.projectContext.selectedProjectId$.subscribe((projectId) => {
      this.selectedProjectId = projectId;
      this.filters.projectId = projectId;
      if (!this.editingId) {
        this.form.patchValue({ projectId });
      }
    });
    this.loadData();
    this.form.controls.amountHt.valueChanges.subscribe((value) => {
      this.updateComputedAmounts(value ?? 0);
    });
    this.updateComputedAmounts(this.form.controls.amountHt.value ?? 0);
  }

  loadData(): void {
    this.api.getExpenses().subscribe({ next: (data) => (this.expenses = data) });
    this.api.getExpenseCategories().subscribe({ next: (data) => (this.categories = data) });
    this.api.getProjects().subscribe({ next: (data) => (this.projects = data) });
    this.api.getSuppliers().subscribe({ next: (data) => (this.suppliers = data) });
  }

  getCategoryName(row: Expense): string {
    if (row.category?.name) return row.category.name;
    const categoryId = row.categoryId;
    return this.categories.find((item) => item.id === categoryId)?.name ?? '-';
  }

  getProjectName(row: Expense): string {
    if (row.project?.name) return row.project.name;
    const projectId = row.projectId;
    return this.projects.find((item) => item.id === projectId)?.name ?? '-';
  }

  get currentProjectName(): string {
    if (!this.selectedProjectId) return 'Aucun projet sélectionné';
    return this.projects.find((item) => item.id === this.selectedProjectId)?.name ?? 'Projet en cours';
  }

  getSupplierName(row: Expense): string {
    if (row.supplier?.name) return row.supplier.name;
    const supplierId = row.supplierId;
    if (!supplierId) return '-';
    return this.suppliers.find((item) => item.id === supplierId)?.name ?? '-';
  }

  getPaymentMethodLabel(value?: string | null): string {
    return this.paymentMethods.find((item) => item.value === value)?.label ?? (value || '-');
  }

  get filteredExpenses(): Expense[] {
    return this.expenses.filter((row) => {
      const term = this.filters.search.trim().toLowerCase();
      const rowDate = row.expenseDate ?? '';
      const matchSearch = !term || [row.reference, row.description, row.documentNumber, row.notes, this.getCategoryName(row), this.getProjectName(row), this.getSupplierName(row)]
        .some((value) => (value ?? '').toString().toLowerCase().includes(term));
      const projectId = row.project?.id ?? row.projectId;
      const matchCategory = !this.filters.categoryId || (row.category?.id ?? row.categoryId) === this.filters.categoryId;
      const matchProject = !this.filters.projectId || projectId === this.filters.projectId;
      const matchSelectedProject = !this.selectedProjectId || projectId === this.selectedProjectId;
      const matchSupplier = !this.filters.supplierId || (row.supplier?.id ?? row.supplierId) === this.filters.supplierId;
      const matchFrom = !this.filters.dateFrom || rowDate >= this.filters.dateFrom;
      const matchTo = !this.filters.dateTo || rowDate <= this.filters.dateTo;
      return matchSearch && matchCategory && matchProject && matchSelectedProject && matchSupplier && matchFrom && matchTo;
    });
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
        this.loadData();
      },
      error: () => this.ui.error('Enregistrement impossible', 'La dépense n’a pas pu être enregistrée.')
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
      vatAmount: expense.vatAmount,
      amountTtc: expense.amountTtc,
      paymentMethod: expense.paymentMethod ?? 'OTHER',
      documentNumber: expense.documentNumber ?? '',
      attachmentName: expense.attachmentName ?? '',
      attachmentUrl: expense.attachmentUrl ?? '',
      notes: expense.notes ?? '',
      categoryId: expense.category?.id ?? expense.categoryId ?? null,
      projectId: expense.project?.id ?? expense.projectId ?? null,
      supplierId: expense.supplier?.id ?? expense.supplierId ?? null
    });
    if (this.selectedProjectId) {
      this.form.patchValue({ projectId: this.selectedProjectId });
    }
    this.updateComputedAmounts(expense.amountHt ?? 0);
  }

  resetForm(): void {
    this.editingId = null;
    this.expenseDialogVisible = false;
    this.form.reset({
      reference: '',
      amountHt: 0,
      vatAmount: 0,
      amountTtc: 0,
      paymentMethod: 'OTHER',
      documentNumber: '',
      attachmentName: '',
      attachmentUrl: '',
      notes: '',
      supplierId: null,
      categoryId: null,
      projectId: this.selectedProjectId,
      description: '',
      expenseDate: ''
    });
    this.updateComputedAmounts(0);
  }

  openCreateDialog(): void {
    this.resetForm();
    this.expenseDialogVisible = true;
  }

  private updateComputedAmounts(amountHt: number): void {
    const normalizedHt = Number(amountHt) || 0;
    const vatAmount = this.roundAmount(normalizedHt * this.vatRate);
    const amountTtc = this.roundAmount(normalizedHt + vatAmount);
    this.form.patchValue(
      {
        vatAmount,
        amountTtc
      },
      { emitEvent: false }
    );
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
        this.loadData();
        this.form.patchValue({ categoryId: created.id });
        this.closeCategoryDialog();
      },
      error: () => this.ui.error('Enregistrement impossible', 'La catégorie de dépense n’a pas pu être enregistrée.')
    });
  }
}
