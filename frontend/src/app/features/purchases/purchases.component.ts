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
  selector: 'app-purchases',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, TableModule, CardModule, ButtonModule, InputTextModule, InputNumberModule, DropdownModule, DialogModule, TagModule],
  templateUrl: './purchases.component.html',
  styleUrl: './purchases.component.css'
})
export class PurchasesComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly ui = inject(UiService);
  private readonly projectContext = inject(ProjectContextService);

  purchases: ClientPurchase[] = [];
  apartments: Apartment[] = [];
  advances: ClientAdvance[] = [];
  clients: Client[] = [];
  projects: Project[] = [];
  editingId: number | null = null;
  dialogVisible = false;
  selectedProjectId: number | null = null;
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
    return this.clients.find((item) => item.id === clientId)?.fullName ?? '-';
  }

  getProjectName(projectId?: number | null): string {
    return this.projects.find((item) => item.id === projectId)?.name ?? '-';
  }

  get currentProjectName(): string {
    if (!this.selectedProjectId) return 'Aucun projet sélectionné';
    return this.projects.find((item) => item.id === this.selectedProjectId)?.name ?? 'Projet en cours';
  }

  get selectedFormProjectId(): number | null {
    return this.selectedProjectId ?? this.form.get('projectId')?.value ?? null;
  }

  get selectedApartmentId(): number | null {
    return this.form.get('apartmentId')?.value ?? null;
  }

  get projectClients(): Client[] {
    if (!this.selectedProjectId) {
      return this.clients;
    }
    return this.clients.filter((client) => (client.project?.id ?? client.projectId) === this.selectedProjectId);
  }

  get availableApartments(): Apartment[] {
    const projectId = this.selectedFormProjectId;
    const clientId = this.form.get('clientId')?.value;
    const currentApartmentId = this.selectedApartmentId;

    return this.apartments.filter((apartment) => {
      const apartmentProjectId = apartment.project?.id ?? apartment.projectId;
      if (projectId && apartmentProjectId !== projectId) {
        return false;
      }

      const apartmentPurchase = this.purchases.find(
        (purchase) => (purchase.apartment?.id ?? purchase.apartmentId) === apartment.id && purchase.id !== this.editingId
      );
      if (apartmentPurchase) {
        return false;
      }

      if (currentApartmentId && apartment.id === currentApartmentId) {
        return true;
      }

      const acquirerId = apartment.acquirer?.id ?? apartment.acquirerId ?? null;
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

    return this.advances
      .filter((advance) => (advance.apartment?.id ?? advance.apartmentId) === this.selectedApartmentId)
      .reduce((sum, advance) => sum + (advance.amount ?? 0), 0);
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

  get filteredPurchaseCount(): number {
    return this.filteredPurchases.length;
  }

  get filteredDeclaredTotal(): number {
    return this.filteredPurchases.reduce((sum, purchase) => sum + (purchase.totalAmount ?? 0), 0);
  }

  get filteredCollectedTotal(): number {
    return this.filteredPurchases.reduce((sum, purchase) => sum + this.getPurchaseCollectedAmount(purchase), 0);
  }

  get filteredRemainingTotal(): number {
    return this.filteredPurchases.reduce((sum, purchase) => sum + this.getPurchaseRemainingAmount(purchase), 0);
  }

  get filteredPaidCount(): number {
    return this.filteredPurchases.filter((purchase) => purchase.completed || purchase.paymentStatus === 'PAID').length;
  }

  form = this.fb.group({
    reference: ['', [Validators.required]],
    purchaseDate: ['', [Validators.required]],
    contractDate: [''],
    totalAmount: [0, [Validators.required]],
    paidAmount: [0],
    assetDescription: ['', [Validators.required]],
    attachmentName: [''],
    attachmentUrl: [''],
    notes: [''],
    clientId: [null as number | null, [Validators.required]],
    apartmentId: [null as number | null, [Validators.required]],
    projectId: [null as number | null, [Validators.required]]
  });

  ngOnInit(): void {
    this.projectContext.selectedProjectId$.subscribe((projectId) => {
      this.selectedProjectId = projectId;
      this.filters.projectId = projectId;
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
    this.loadData();
  }

  loadData(): void {
    this.api.getPurchases().subscribe({ next: (data) => (this.purchases = data) });
    this.api.getApartments().subscribe({ next: (data) => (this.apartments = data) });
    this.api.getAdvances().subscribe({ next: (data) => (this.advances = data) });
    this.api.getClients().subscribe({ next: (data) => (this.clients = data) });
    this.api.getProjects().subscribe({ next: (data) => (this.projects = data) });
  }

  get filteredPurchases(): ClientPurchase[] {
    return this.purchases.filter((row) => {
      const term = this.filters.search.trim().toLowerCase();
      const rowDate = row.purchaseDate ?? '';
      const matchSearch = !term || [
        row.reference,
        row.assetDescription,
        row.notes,
        this.getClientName(row.client?.id ?? row.clientId),
        this.getProjectName(row.project?.id ?? row.projectId),
        this.getApartmentName(row.apartment?.id ?? row.apartmentId),
        this.getPaymentStatusLabel(row.paymentStatus)
      ]
        .some((value) => (value ?? '').toString().toLowerCase().includes(term));
      const projectId = row.project?.id ?? row.projectId;
      const matchClient = !this.filters.clientId || (row.client?.id ?? row.clientId) === this.filters.clientId;
      const matchProject = !this.filters.projectId || projectId === this.filters.projectId;
      const matchSelectedProject = !this.selectedProjectId || projectId === this.selectedProjectId;
      const matchStatus = !this.filters.paymentStatus || row.paymentStatus === this.filters.paymentStatus;
      const matchFrom = !this.filters.dateFrom || rowDate >= this.filters.dateFrom;
      const matchTo = !this.filters.dateTo || rowDate <= this.filters.dateTo;
      return matchSearch && matchClient && matchProject && matchSelectedProject && matchStatus && matchFrom && matchTo;
    });
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
      attachmentName: purchase.attachmentName ?? '',
      attachmentUrl: purchase.attachmentUrl ?? '',
      notes: purchase.notes ?? '',
      clientId: purchase.client?.id ?? purchase.clientId ?? null,
      apartmentId: purchase.apartment?.id ?? purchase.apartmentId ?? null,
      projectId: this.selectedProjectId ?? purchase.project?.id ?? purchase.projectId ?? null
    });
  }

  remove(row: ClientPurchase): void {
    if (!row.id) return;
    this.ui.confirmDelete(`Supprimer l’achat client ${row.reference} ?`, () => {
      this.api.deletePurchase(row.id!).subscribe({
        next: () => {
          this.ui.success('Achat supprimé', 'L’achat client a été supprimé.');
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
      attachmentName: '',
      attachmentUrl: '',
      notes: '',
      clientId: null,
      apartmentId: null,
      projectId: this.selectedProjectId
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
      projectId: this.selectedProjectId,
      paymentStatus: '',
      dateFrom: '',
      dateTo: ''
    };
  }

  getApartmentName(apartmentId?: number | null): string {
    return this.apartments.find((item) => item.id === apartmentId)?.apartmentNumber ?? '-';
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
    const apartment = this.apartments.find((item) => item.id === apartmentId);
    if (!apartment) {
      return;
    }

    const apartmentClientId = apartment.acquirer?.id ?? apartment.acquirerId ?? null;
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

    const apartment = this.apartments.find((item) => item.id === apartmentId);
    if (!apartment) {
      return;
    }

    const apartmentClientId = apartment.acquirer?.id ?? apartment.acquirerId ?? null;
    if (apartmentClientId && clientId && apartmentClientId !== clientId) {
      this.form.patchValue({ apartmentId: null }, { emitEvent: false });
      this.ui.info('Appartement incompatible', 'Merci de choisir un appartement libre ou déjà affecté à ce client.');
    }
  }
}
