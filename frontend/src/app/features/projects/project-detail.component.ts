import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { ApiService } from '../../core/services/api.service';
import { AuditLog, ClientAdvance, ClientPurchase, DashboardSummary, Expense, Project } from '../../shared/models/models';

/** Rows shown in each of the detail tables; this screen is a summary, not a register. */
const DETAIL_ROWS = 25;

@Component({
  selector: 'app-project-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, CardModule, TableModule, TagModule, ButtonModule],
  templateUrl: './project-detail.component.html',
  styleUrl: './project-detail.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ProjectDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(ApiService);

  readonly project = signal<Project | undefined>(undefined);
  readonly expenses = signal<Expense[]>([]);
  readonly purchases = signal<ClientPurchase[]>([]);
  readonly advances = signal<ClientAdvance[]>([]);
  readonly auditLogs = signal<AuditLog[]>([]);
  readonly summary = signal<DashboardSummary | undefined>(undefined);

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!id) {
      return;
    }

    const page = { page: 0, size: DETAIL_ROWS };

    this.api.getProject(id).subscribe({ next: (data) => this.project.set(data) });
    // The detail tables show the most recent rows; the totals below come from the backend's
    // own aggregate rather than from summing whatever happened to be loaded (PERF-02).
    this.api.getExpenses({ projectId: id }, page).subscribe({ next: (data) => this.expenses.set(data.content) });
    this.api.getPurchases({ projectId: id }, page).subscribe({ next: (data) => this.purchases.set(data.content) });
    this.api.getAdvances({ projectId: id }, page).subscribe({ next: (data) => this.advances.set(data.content) });
    this.api.getDashboardSummary({ projectId: id }).subscribe({ next: (data) => this.summary.set(data) });
    this.api.getAuditLogs('PROJECT', id).subscribe({ next: (data) => this.auditLogs.set(data) });
  }

  get totalExpenses(): number {
    return this.summary()?.totalExpenses ?? 0;
  }

  get totalPurchases(): number {
    return this.summary()?.totalPurchases ?? 0;
  }

  /** Everything collected: advances plus payments made directly on the contracts. */
  get totalAdvances(): number {
    return this.summary()?.totalAdvances ?? 0;
  }

  get remainingToCollect(): number {
    return this.summary()?.totalRemainingFromClients ?? 0;
  }

  get budgetUsage(): number {
    const budget = this.project()?.budget ?? 0;
    if (!budget) return 0;
    return Math.min(100, Math.round((this.totalExpenses / budget) * 100));
  }

  get estimatedMargin(): number {
    return this.totalPurchases - this.totalExpenses;
  }

  getStatusLabel(status?: string): string {
    switch (status) {
      case 'PLANNED':
        return 'Planifié';
      case 'IN_PROGRESS':
        return 'En cours';
      case 'COMPLETED':
        return 'Terminé';
      case 'CANCELLED':
        return 'Annulé';
      default:
        return status || 'Non défini';
    }
  }

  getStatusSeverity(status?: string): 'success' | 'info' | 'warning' | 'danger' | 'secondary' {
    switch (status) {
      case 'COMPLETED':
        return 'success';
      case 'IN_PROGRESS':
        return 'info';
      case 'PLANNED':
        return 'warning';
      case 'CANCELLED':
        return 'danger';
      default:
        return 'secondary';
    }
  }
}
