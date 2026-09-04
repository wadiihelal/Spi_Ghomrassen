import { ChangeDetectionStrategy, Component, DestroyRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DropdownModule } from 'primeng/dropdown';
import { TagModule } from 'primeng/tag';
import { ApiService } from '../../core/services/api.service';
import { LazyTable } from '../../core/services/lazy-table';
import { AuditFilter, AuditLog } from '../../shared/models/models';

/** French names for what the backend records; the key is the backend's entity type. */
const ENTITY_LABELS: Record<string, string> = {
  PROJECT: 'Projet',
  PROJECT_CONTEXT: 'Projet de travail',
  APARTMENT: 'Appartement',
  CLIENT: 'Client',
  SUPPLIER: 'Fournisseur',
  SUPPLIER_TYPE: 'Type de fournisseur',
  SUPPLIER_INVOICE: 'Facture fournisseur',
  EXPENSE: 'Dépense',
  EXPENSE_CATEGORY: 'Catégorie de dépense',
  PURCHASE: 'Contrat de vente',
  ADVANCE: 'Encaissement client'
};

const ACTION_LABELS: Record<string, string> = {
  CREATE: 'Création',
  UPDATE: 'Modification',
  DELETE: 'Suppression',
  SCHEDULE: 'Échéancier',
  PAYMENT: 'Règlement',
  ATTACH: 'Pièce jointe',
  DETACH: 'Pièce retirée',
  CLEAR: 'Réinitialisation',
  SELECT: 'Sélection'
};

/**
 * Journal des opérations.
 *
 * <p>Read-only view of the audit trail the backend writes on every change. It is the one
 * screen that is not scoped to a project: an operator asking « who changed this ? » needs the
 * whole company's history.</p>
 */
@Component({
  selector: 'app-audit',
  standalone: true,
  imports: [CommonModule, FormsModule, TableModule, CardModule, ButtonModule, InputTextModule,
    DropdownModule, TagModule],
  templateUrl: './audit.component.html',
  styleUrl: './audit.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AuditComponent {
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly table = new LazyTable<AuditLog>(
    (query) => this.api.getAuditJournal(this.filters, query),
    this.destroyRef
  );

  filters: AuditFilter = { entityType: null, dateFrom: '', dateTo: '' };

  readonly entityOptions = [
    { label: 'Tous les objets', value: null },
    ...Object.entries(ENTITY_LABELS).map(([value, label]) => ({ label, value }))
  ];

  entityLabel(type: string): string {
    return ENTITY_LABELS[type] ?? type;
  }

  actionLabel(action: string): string {
    return ACTION_LABELS[action] ?? action;
  }

  actionSeverity(action: string): 'success' | 'info' | 'warning' | 'danger' | 'secondary' {
    switch (action) {
      case 'CREATE': return 'success';
      case 'UPDATE': case 'SCHEDULE': case 'ATTACH': return 'info';
      case 'PAYMENT': return 'warning';
      case 'DELETE': case 'DETACH': return 'danger';
      default: return 'secondary';
    }
  }

  resetFilters(): void {
    this.filters = { entityType: null, dateFrom: '', dateTo: '' };
    this.table.onFilterChange();
  }
}
