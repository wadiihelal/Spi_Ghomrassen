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
import { Project, Supplier, SupplierInvoice } from '../../shared/models/models';
import { ApiService } from '../../core/services/api.service';
import { UiService } from '../../core/services/ui.service';
import { ProjectContextService } from '../../core/services/project-context.service';
import { environment } from '../../../environments/environment';

interface GeneratedWithholdingRow {
  invoiceDate: string;
  invoiceNumber: string;
  supplierName: string;
  projectName: string;
  amountHt: number;
  amountTtc: number;
  withholdingAmount: number;
  netToPay: number;
}

@Component({
  selector: 'app-supplier-invoices',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, TableModule, CardModule, ButtonModule, InputTextModule, InputNumberModule, DropdownModule, DialogModule],
  templateUrl: './supplier-invoices.component.html',
  styleUrl: './supplier-invoices.component.css'
})
export class SupplierInvoicesComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly ui = inject(UiService);
  private readonly projectContext = inject(ProjectContextService);

  invoices: SupplierInvoice[] = [];
  suppliers: Supplier[] = [];
  projects: Project[] = [];
  editingId: number | null = null;
  dialogVisible = false;
  selectedProjectId: number | null = null;
  readonly vatRate = environment.vatRate;
  readonly withholdingRate = environment.withholdingRate;
  filters = {
    projectId: null as number | null,
    supplierId: null as number | null,
    search: ''
  };

  get currentProjectName(): string {
    if (!this.selectedProjectId) return 'Aucun projet sélectionné';
    return this.projects.find((item) => item.id === this.selectedProjectId)?.name ?? 'Projet en cours';
  }

  form = this.fb.group({
    invoiceNumber: ['', [Validators.required]],
    invoiceDate: ['', [Validators.required]],
    amountHt: [0, [Validators.required]],
    vatAmount: [{ value: 0, disabled: true }, [Validators.required]],
    amountTtc: [{ value: 0, disabled: true }, [Validators.required]],
    withholdingAmount: [{ value: 0, disabled: true }, [Validators.required]],
    netToPay: [{ value: 0, disabled: true }, [Validators.required]],
    attachmentName: [''],
    attachmentUrl: [''],
    detail: [''],
    supplierId: [null as number | null, [Validators.required]],
    projectId: [null as number | null, [Validators.required]]
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
    this.form.controls.amountHt.valueChanges.subscribe((value) => this.updateComputedAmounts(value ?? 0));
    this.updateComputedAmounts(this.form.controls.amountHt.value ?? 0);
  }

  loadData(): void {
    this.api.getSupplierInvoices().subscribe({ next: (data) => (this.invoices = data) });
    this.api.getSuppliers().subscribe({ next: (data) => (this.suppliers = data) });
    this.api.getProjects().subscribe({ next: (data) => (this.projects = data) });
  }

  get filteredInvoices(): SupplierInvoice[] {
    return this.invoices.filter((row) => {
      const term = this.filters.search.trim().toLowerCase();
      const matchSearch = !term || [row.invoiceNumber, row.detail, row.supplier?.name, row.project?.name]
        .some((value) => (value ?? '').toString().toLowerCase().includes(term));
      const projectId = row.project?.id ?? row.projectId;
      const matchProject = !this.filters.projectId || projectId === this.filters.projectId;
      const matchSelectedProject = !this.selectedProjectId || projectId === this.selectedProjectId;
      const matchSupplier = !this.filters.supplierId || (row.supplier?.id ?? row.supplierId) === this.filters.supplierId;
      return matchSearch && matchProject && matchSelectedProject && matchSupplier;
    });
  }

  get generatedWithholdings(): GeneratedWithholdingRow[] {
    return this.filteredInvoices
      .filter((row) => (row.withholdingAmount ?? 0) > 0)
      .map((row) => ({
        invoiceDate: row.invoiceDate,
        invoiceNumber: row.invoiceNumber,
        supplierName: row.supplier?.name ?? '-',
        projectName: row.project?.name ?? '-',
        amountHt: row.amountHt ?? 0,
        amountTtc: row.amountTtc ?? 0,
        withholdingAmount: row.withholdingAmount ?? 0,
        netToPay: row.netToPay ?? 0
      }))
      .sort((a, b) => (b.invoiceDate ?? '').localeCompare(a.invoiceDate ?? ''));
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
        this.loadData();
      },
      error: () => this.ui.error('Enregistrement impossible', 'La facture fournisseur n’a pas pu être enregistrée.')
    });
  }

  edit(row: SupplierInvoice): void {
    this.editingId = row.id ?? null;
    this.dialogVisible = true;
    this.form.patchValue({
      invoiceNumber: row.invoiceNumber,
      invoiceDate: row.invoiceDate,
      amountHt: row.amountHt,
      vatAmount: row.vatAmount,
      amountTtc: row.amountTtc,
      withholdingAmount: row.withholdingAmount,
      netToPay: row.netToPay,
      attachmentName: row.attachmentName ?? '',
      attachmentUrl: row.attachmentUrl ?? '',
      detail: row.detail ?? '',
      supplierId: row.supplier?.id ?? row.supplierId ?? null,
      projectId: this.selectedProjectId ?? row.project?.id ?? row.projectId ?? null
    });
    this.updateComputedAmounts(row.amountHt ?? 0);
  }

  remove(row: SupplierInvoice): void {
    if (!row.id) return;
    this.ui.confirmDelete(`Supprimer la facture ${row.invoiceNumber} ?`, () => {
      this.api.deleteSupplierInvoice(row.id!).subscribe({
        next: () => {
          this.ui.success('Facture supprimée', 'La facture fournisseur a été supprimée.');
          this.loadData();
          if (this.editingId === row.id) this.resetForm();
        },
        error: () => this.ui.error('Suppression impossible', 'La facture fournisseur n’a pas pu être supprimée.')
      });
    });
  }

  resetForm(): void {
    this.editingId = null;
    this.dialogVisible = false;
    this.form.reset({
      invoiceNumber: '',
      invoiceDate: '',
      amountHt: 0,
      vatAmount: 0,
      amountTtc: 0,
      withholdingAmount: 0,
      netToPay: 0,
      attachmentName: '',
      attachmentUrl: '',
      detail: '',
      supplierId: null,
      projectId: this.selectedProjectId
    });
    this.updateComputedAmounts(0);
  }

  openCreateDialog(): void {
    this.resetForm();
    this.dialogVisible = true;
  }

  exportWithholdingsCsv(): void {
    const rows = this.generatedWithholdings;
    const header = ['Date', 'Facture', 'Fournisseur', 'Projet', 'Montant HT', 'Montant TTC', 'Retenue', 'Net a payer'];
    const lines = [
      header.join(';'),
      ...rows.map((row) => [
        row.invoiceDate,
        row.invoiceNumber,
        row.supplierName,
        row.projectName,
        this.formatDecimal(row.amountHt),
        this.formatDecimal(row.amountTtc),
        this.formatDecimal(row.withholdingAmount),
        this.formatDecimal(row.netToPay)
      ].map((value) => this.escapeCsv(value)).join(';'))
    ];
    const blob = new Blob(['\uFEFF' + lines.join('\n')], { type: 'text/csv;charset=utf-8;' });
    const projectSuffix = this.selectedProjectId ? `-projet-${this.selectedProjectId}` : '';
    this.saveFile(blob, `retenues-source${projectSuffix}.csv`);
  }

  private updateComputedAmounts(amountHt: number): void {
    const normalizedHt = Number(amountHt) || 0;
    const vatAmount = this.round(normalizedHt * this.vatRate);
    const amountTtc = this.round(normalizedHt + vatAmount);
    const withholdingAmount = this.round(normalizedHt * this.withholdingRate);
    const netToPay = this.round(amountTtc - withholdingAmount);
    this.form.patchValue({ vatAmount, amountTtc, withholdingAmount, netToPay }, { emitEvent: false });
  }

  private round(value: number): number {
    return Math.round((value + Number.EPSILON) * 1000) / 1000;
  }

  private formatDecimal(value: number): string {
    return this.round(value).toFixed(3);
  }

  private escapeCsv(value: string | number): string {
    const normalized = String(value ?? '');
    return `"${normalized.replaceAll('"', '""')}"`;
  }

  private saveFile(blob: Blob, fileName: string): void {
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    link.click();
    window.URL.revokeObjectURL(url);
  }
}
