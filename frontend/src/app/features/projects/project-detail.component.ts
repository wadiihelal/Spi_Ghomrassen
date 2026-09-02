import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { ApiService } from '../../core/services/api.service';
import { AuditLog, ClientAdvance, ClientPurchase, Expense, Project } from '../../shared/models/models';

@Component({
  selector: 'app-project-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, CardModule, TableModule, TagModule, ButtonModule],
  templateUrl: './project-detail.component.html',
  styleUrl: './project-detail.component.css'
})
export class ProjectDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(ApiService);

  project?: Project;
  expenses: Expense[] = [];
  purchases: ClientPurchase[] = [];
  advances: ClientAdvance[] = [];
  auditLogs: AuditLog[] = [];

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!id) {
      return;
    }

    this.api.getProject(id).subscribe({ next: (data) => (this.project = data) });
    this.api.getExpenses().subscribe({ next: (data) => (this.expenses = data.filter((item) => (item.project?.id ?? item.projectId) === id)) });
    this.api.getPurchases().subscribe({ next: (data) => (this.purchases = data.filter((item) => (item.project?.id ?? item.projectId) === id)) });
    this.api.getAdvances().subscribe({ next: (data) => (this.advances = data.filter((item) => (item.project?.id ?? item.projectId) === id)) });
    this.api.getAuditLogs('PROJECT', id).subscribe({ next: (data) => (this.auditLogs = data) });
  }

  get totalExpenses(): number {
    return this.expenses.reduce((sum, item) => sum + (item.amountTtc ?? 0), 0);
  }

  get totalPurchases(): number {
    return this.purchases.reduce((sum, item) => sum + (item.totalAmount ?? 0), 0);
  }

  get totalAdvances(): number {
    const advancesAmount = this.advances.reduce((sum, item) => sum + (item.amount ?? 0), 0);
    const directPaymentsAmount = this.purchases.reduce((sum, item) => sum + (item.paidAmount ?? 0), 0);
    return advancesAmount + directPaymentsAmount;
  }

  get remainingToCollect(): number {
    return this.totalPurchases - this.totalAdvances;
  }

  get budgetUsage(): number {
    const budget = this.project?.budget ?? 0;
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
