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
import { TagModule } from 'primeng/tag';
import { ApiService } from '../../core/services/api.service';
import { UiService } from '../../core/services/ui.service';
import { ProjectContextService } from '../../core/services/project-context.service';
import { Apartment, ClientAdvance, ClientPurchase, Client, Project, PurchasePaymentStatus } from '../../shared/models/models';

@Component({
  selector: 'app-advances',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, TableModule, CardModule, ButtonModule, InputTextModule, InputNumberModule, DropdownModule, DialogModule, TagModule],
  templateUrl: './advances.component.html',
  styleUrl: './advances.component.css'
})
export class AdvancesComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly ui = inject(UiService);
  private readonly projectContext = inject(ProjectContextService);

  advances: ClientAdvance[] = [];
  apartments: Apartment[] = [];
  purchases: ClientPurchase[] = [];
  clients: Client[] = [];
  projects: Project[] = [];
  editingId: number | null = null;
  dialogVisible = false;
  dialogProjectId: number | null = null;
  selectedProjectId: number | null = null;
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
    if (row.apartment?.acquirer?.fullName) return row.apartment.acquirer.fullName;
    if (row.client?.fullName) return row.client.fullName;
    const clientId = row.clientId;
    return this.clients.find((item) => item.id === clientId)?.fullName ?? '-';
  }

  getProjectName(row: ClientAdvance): string {
    if (row.apartment?.project?.name) return row.apartment.project.name;
    if (row.project?.name) return row.project.name;
    const projectId = row.projectId;
    return this.projects.find((item) => item.id === projectId)?.name ?? '-';
  }

  getApartmentLabel(apartment?: Apartment | null): string {
    if (!apartment) return '-';
    const clientName = apartment.acquirer?.fullName ?? 'Sans acquéreur';
    const projectName = apartment.project?.name ?? 'Sans projet';
    return `${apartment.apartmentNumber} - ${clientName} - ${projectName}`;
  }

  getApartmentName(row: ClientAdvance): string {
    if (row.apartment?.apartmentNumber) return row.apartment.apartmentNumber;
    const apartmentId = row.apartmentId;
    return this.apartments.find((item) => item.id === apartmentId)?.apartmentNumber ?? '-';
  }

  get selectedApartment(): Apartment | undefined {
    const apartmentId = this.form.get('apartmentId')?.value;
    return this.availableApartments.find((item) => item.id === apartmentId)
      ?? this.apartments.find((item) => item.id === apartmentId);
  }

  get selectedApartmentId(): number | null {
    return this.form.get('apartmentId')?.value ?? null;
  }

  get selectedApartmentPurchase(): ClientPurchase | undefined {
    return this.getPurchaseForApartment(this.selectedApartmentId);
  }

  get availableApartments(): Apartment[] {
    return this.apartments.filter((apartment) => {
      const activeProjectId = this.dialogProjectId ?? this.selectedProjectId;
      const matchesProject = !activeProjectId || apartment.project?.id === activeProjectId || apartment.projectId === activeProjectId;
      return matchesProject && !!(apartment.acquirer?.id ?? apartment.acquirerId);
    });
  }

  get currentProjectName(): string {
    const activeProjectId = this.dialogProjectId ?? this.selectedProjectId;
    if (!activeProjectId) return 'Aucun projet sélectionné';
    return this.projects.find((item) => item.id === activeProjectId)?.name ?? 'Projet en cours';
  }

  get projectClients(): Client[] {
    if (!this.selectedProjectId) {
      return this.clients;
    }
    return this.clients.filter((client) => (client.project?.id ?? client.projectId) === this.selectedProjectId);
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
    return this.getAdvancesAmountForApartment(this.selectedApartmentId, this.editingId);
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

  get filteredAdvanceCount(): number {
    return this.filteredAdvances.length;
  }

  get filteredAdvanceTotal(): number {
    return this.filteredAdvances.reduce((sum, advance) => sum + (advance.amount ?? 0), 0);
  }

  get filteredBankTransferTotal(): number {
    return this.filteredAdvances
      .filter((advance) => advance.paymentMethod === 'BANK_TRANSFER')
      .reduce((sum, advance) => sum + (advance.amount ?? 0), 0);
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
    this.projectContext.selectedProjectId$.subscribe((projectId) => {
      this.selectedProjectId = projectId;
      if (!this.dialogVisible || !this.editingId) {
        this.dialogProjectId = projectId;
      }
      this.filters.projectId = projectId;
      if (!this.editingId) {
        const currentApartmentId = this.form.get('apartmentId')?.value;
        const apartmentStillVisible = this.availableApartments.some((item) => item.id === currentApartmentId);
        if (!apartmentStillVisible) {
          this.form.patchValue({ apartmentId: null });
        }
      }
    });
    this.loadData();
  }

  loadData(): void {
    this.api.getAdvances().subscribe({ next: (data) => (this.advances = data) });
    this.api.getApartments().subscribe({ next: (data) => (this.apartments = data) });
    this.api.getPurchases().subscribe({ next: (data) => (this.purchases = data) });
    this.api.getClients().subscribe({ next: (data) => (this.clients = data) });
    this.api.getProjects().subscribe({ next: (data) => (this.projects = data) });
  }

  get filteredAdvances(): ClientAdvance[] {
    return this.advances.filter((row) => {
      const term = this.filters.search.trim().toLowerCase();
      const rowDate = row.advanceDate ?? '';
      const matchSearch = !term || [row.reference, row.notes, this.getClientName(row), this.getProjectName(row), this.getApartmentName(row), this.getPaymentMethodLabel(row.paymentMethod)]
        .some((value) => (value ?? '').toString().toLowerCase().includes(term));
      const projectId = row.apartment?.project?.id ?? row.project?.id ?? row.projectId;
      const clientId = row.apartment?.acquirer?.id ?? row.client?.id ?? row.clientId;
      const matchClient = !this.filters.clientId || clientId === this.filters.clientId;
      const matchProject = !this.filters.projectId || projectId === this.filters.projectId;
      const matchSelectedProject = !this.selectedProjectId || projectId === this.selectedProjectId;
      const matchPayment = !this.filters.paymentMethod || row.paymentMethod === this.filters.paymentMethod;
      const matchFrom = !this.filters.dateFrom || rowDate >= this.filters.dateFrom;
      const matchTo = !this.filters.dateTo || rowDate <= this.filters.dateTo;
      return matchSearch && matchClient && matchProject && matchSelectedProject && matchPayment && matchFrom && matchTo;
    });
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
        this.loadData();
      },
      error: () => this.ui.error('Enregistrement impossible', 'L’acompte n’a pas pu être enregistré.')
    });
  }

  edit(advance: ClientAdvance): void {
    this.editingId = advance.id ?? null;
    this.dialogVisible = true;
    this.dialogProjectId = advance.apartment?.project?.id ?? advance.project?.id ?? advance.projectId ?? this.selectedProjectId;
    this.form.patchValue({
      reference: advance.reference ?? '',
      advanceDate: advance.advanceDate ?? '',
      amount: advance.amount,
      paymentMethod: advance.paymentMethod ?? 'BANK_TRANSFER',
      attachmentName: advance.attachmentName ?? '',
      attachmentUrl: advance.attachmentUrl ?? '',
      notes: advance.notes ?? '',
      apartmentId: advance.apartment?.id ?? advance.apartmentId ?? null
    });
  }

  resetForm(): void {
    this.editingId = null;
    this.dialogVisible = false;
    this.dialogProjectId = this.selectedProjectId;
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
    this.dialogProjectId = this.selectedProjectId;
    this.dialogVisible = true;
  }

  resetFilters(): void {
    this.filters = {
      search: '',
      clientId: null,
      projectId: this.selectedProjectId,
      paymentMethod: '',
      dateFrom: '',
      dateTo: ''
    };
  }

  getPurchaseForApartment(apartmentId?: number | null): ClientPurchase | undefined {
    if (!apartmentId) {
      return undefined;
    }

    return this.purchases.find((purchase) => (purchase.apartment?.id ?? purchase.apartmentId) === apartmentId);
  }

  getPurchaseForAdvance(advance: ClientAdvance): ClientPurchase | undefined {
    return this.getPurchaseForApartment(advance.apartment?.id ?? advance.apartmentId);
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
    const apartmentId = advance.apartment?.id ?? advance.apartmentId;
    const purchase = this.getPurchaseForApartment(apartmentId);
    if (!purchase) {
      return null;
    }

    return Math.max(0, (purchase.totalAmount ?? 0) - (purchase.paidAmount ?? 0) - this.getAdvancesAmountForApartment(apartmentId));
  }

  private getAdvancesAmountForApartment(apartmentId?: number | null, excludedAdvanceId?: number | null): number {
    if (!apartmentId) {
      return 0;
    }

    return this.advances
      .filter((advance) => (advance.apartment?.id ?? advance.apartmentId) === apartmentId && advance.id !== excludedAdvanceId)
      .reduce((sum, advance) => sum + (advance.amount ?? 0), 0);
  }
}
