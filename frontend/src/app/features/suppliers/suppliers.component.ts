import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, ViewChild, inject, signal } from '@angular/core';
import { FieldLabels, missingFieldsMessage } from '../../core/services/required-fields';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Table, TableModule } from 'primeng/table';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DropdownModule } from 'primeng/dropdown';
import { DialogModule } from 'primeng/dialog';
import { ApiService } from '../../core/services/api.service';
import { UiService } from '../../core/services/ui.service';
import { Supplier, SupplierTypeOption, VatRateOption } from '../../shared/models/models';


const SUPPLIER_FIELDS: FieldLabels = {
  name: 'Nom',
};

const SUPPLIER_TYPE_FIELDS: FieldLabels = {
  label: 'Libellé',
};

@Component({
  selector: 'app-suppliers',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, TableModule, CardModule, ButtonModule, InputTextModule, DropdownModule, DialogModule],
  templateUrl: './suppliers.component.html',
  styleUrl: './suppliers.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SuppliersComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly ui = inject(UiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);

  readonly suppliers = signal<Supplier[]>([]);
  editingId: number | null = null;
  dialogVisible = false;
  readonly supplierTypes = signal<SupplierTypeOption[]>([]);
  supplierTypeDialogVisible = false;
  readonly vatRates = signal<VatRateOption[]>([]);

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

  /** Text in the list's search box; prefilled by the global search's ?search= (UX-08). */
  searchTerm = '';
  @ViewChild('dt') private table?: Table;

  ngOnInit(): void {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => (this.searchTerm = params.get('search') ?? this.searchTerm));
    this.loadSuppliers();
    this.loadSupplierTypes();
    this.api.getVatRates().subscribe({ next: (data) => this.vatRates.set(data) });
  }

  loadSuppliers(): void {
    this.api.getSuppliers().subscribe({
      next: (data) => {
        this.suppliers.set(data);
        this.applySearchTerm();
      }
    });
  }

  loadSupplierTypes(): void {
    this.api.getSupplierTypes().subscribe({
      next: (data) => this.supplierTypes.set(data.filter((item) => item.active !== false))
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.ui.info('Formulaire incomplet',
        missingFieldsMessage(this.form, SUPPLIER_FIELDS, 'Merci de remplir les champs obligatoires du fournisseur.'));
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
      this.ui.info('Formulaire incomplet',
        missingFieldsMessage(this.supplierTypeForm, SUPPLIER_TYPE_FIELDS, 'Merci de remplir les champs obligatoires du type fournisseur.'));
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

  /** Pushes a prefilled search term into the table: PrimeNG filters only on the input event. */
  private applySearchTerm(): void {
    if (this.searchTerm) {
      // The table renders its rows on the next tick; filtering before that finds nothing.
      setTimeout(() => this.table?.filterGlobal(this.searchTerm, 'contains'));
    }
  }
}
