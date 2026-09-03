import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DropdownModule } from 'primeng/dropdown';
import { DialogModule } from 'primeng/dialog';
import { ApiService } from '../../core/services/api.service';
import { UiService } from '../../core/services/ui.service';
import { Supplier, SupplierTypeOption, VatRateOption } from '../../shared/models/models';

@Component({
  selector: 'app-suppliers',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, TableModule, CardModule, ButtonModule, InputTextModule, DropdownModule, DialogModule],
  templateUrl: './suppliers.component.html',
  styleUrl: './suppliers.component.css'
})
export class SuppliersComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly ui = inject(UiService);

  suppliers: Supplier[] = [];
  editingId: number | null = null;
  dialogVisible = false;
  supplierTypes: SupplierTypeOption[] = [];
  supplierTypeDialogVisible = false;
  vatRates: VatRateOption[] = [];

  form = this.fb.group({
    name: ['', [Validators.required]],
    fiscalId: [''],
    phone: [''],
    email: [''],
    address: [''],
    defaultVatRate: [null as number | null],
    typeId: [null as number | null],
    active: [true]
  });

  supplierTypeForm = this.fb.group({
    label: ['', [Validators.required]],
    active: [true]
  });

  ngOnInit(): void {
    this.loadSuppliers();
    this.loadSupplierTypes();
    this.api.getVatRates().subscribe({ next: (data) => (this.vatRates = data) });
  }

  loadSuppliers(): void {
    this.api.getSuppliers().subscribe({
      next: (data) => (this.suppliers = data)
    });
  }

  loadSupplierTypes(): void {
    this.api.getSupplierTypes().subscribe({
      next: (data) => (this.supplierTypes = data.filter((item) => item.active !== false))
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.ui.info('Formulaire incomplet', 'Merci de remplir les champs obligatoires du fournisseur.');
      return;
    }

    const payload = this.form.getRawValue() as Supplier;
    const request$ = this.editingId
      ? this.api.updateSupplier(this.editingId, payload)
      : this.api.createSupplier(payload);

    request$.subscribe({
      next: () => {
        this.ui.success(this.editingId ? 'Fournisseur modifié' : 'Fournisseur ajouté', 'Les informations du fournisseur ont été enregistrées.');
        this.resetForm();
        this.loadSuppliers();
      }
    });
  }

  edit(supplier: Supplier): void {
    this.editingId = supplier.id ?? null;
    this.dialogVisible = true;
    this.form.patchValue({
      name: supplier.name,
      fiscalId: supplier.fiscalId ?? '',
      phone: supplier.phone ?? '',
      email: supplier.email ?? '',
      address: supplier.address ?? '',
      defaultVatRate: supplier.defaultVatRate ?? null,
      typeId: supplier.typeId ?? null,
      active: supplier.active ?? true
    });
  }

  remove(supplier: Supplier): void {
    if (!supplier.id) {
      return;
    }

    this.ui.confirmDelete(`Supprimer le fournisseur ${supplier.name} ?`, () => {
      this.api.deleteSupplier(supplier.id!).subscribe({
        next: () => {
          if (this.editingId === supplier.id) {
            this.resetForm();
          }
          this.ui.success('Fournisseur supprimé', 'Le fournisseur a été supprimé avec succès.');
          this.loadSuppliers();
        }
      });
    });
  }

  resetForm(): void {
    this.editingId = null;
    this.dialogVisible = false;
    this.form.reset({
      name: '',
      fiscalId: '',
      phone: '',
      email: '',
      address: '',
      defaultVatRate: null,
      typeId: null,
      active: true
    });
  }

  openCreateDialog(): void {
    this.resetForm();
    this.dialogVisible = true;
  }

  openSupplierTypeDialog(): void {
    this.supplierTypeDialogVisible = true;
  }

  closeSupplierTypeDialog(): void {
    this.supplierTypeDialogVisible = false;
    this.supplierTypeForm.reset({ label: '', active: true });
  }

  submitSupplierType(): void {
    if (this.supplierTypeForm.invalid) {
      this.supplierTypeForm.markAllAsTouched();
      this.ui.info('Formulaire incomplet', 'Merci de remplir les champs obligatoires du type fournisseur.');
      return;
    }

    this.api.createSupplierType(this.supplierTypeForm.getRawValue() as SupplierTypeOption).subscribe({
      next: (created) => {
        this.ui.success('Type ajouté', 'Le type fournisseur a été enregistré.');
        this.loadSupplierTypes();
        this.form.patchValue({ typeId: created.id });
        this.closeSupplierTypeDialog();
      }
    });
  }
}
