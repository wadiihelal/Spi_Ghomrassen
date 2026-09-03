import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CardModule } from 'primeng/card';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { ProjectContextService } from '../../core/services/project-context.service';
import { DashboardSummary, ClientStatement, Project, Expense, ClientPurchase, ClientAdvance, ReportScopeParams } from '../../shared/models/models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, CardModule, ButtonModule, ProgressSpinnerModule, TableModule, TagModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly projectContext = inject(ProjectContextService);

  loading = true;
  summary?: DashboardSummary;
  statements: ClientStatement[] = [];
  projects: Project[] = [];
  expenses: Expense[] = [];
  purchases: ClientPurchase[] = [];
  advances: ClientAdvance[] = [];
  selectedProjectId: number | null = null;

  ngOnInit(): void {
    this.projectContext.selectedProjectId$.subscribe((projectId) => {
      this.selectedProjectId = projectId;
      this.loadScopedAggregates();
    });
    let completedCalls = 0;
    const done = () => {
      completedCalls += 1;
      if (completedCalls >= 4) this.loading = false;
    };

    this.api.getProjects().subscribe({
      next: (data) => (this.projects = data),
      error: () => undefined,
      complete: done
    });

    this.api.getExpenses().subscribe({
      next: (data) => (this.expenses = data),
      error: () => undefined,
      complete: done
    });

    this.api.getPurchases().subscribe({
      next: (data) => (this.purchases = data),
      error: () => undefined,
      complete: done
    });

    this.api.getAdvances().subscribe({
      next: (data) => (this.advances = data),
      error: () => undefined,
      complete: done
    });
  }

  /**
   * Summary and client statements are aggregated by the backend, so they must be re-requested
   * whenever the selected project changes (RPT-02).
   */
  private loadScopedAggregates(): void {
    const scope: ReportScopeParams = { projectId: this.selectedProjectId };
    this.api.getDashboardSummary(scope).subscribe({
      next: (data) => (this.summary = data),
      error: () => undefined
    });
    this.api.getClientStatements(scope).subscribe({
      next: (data) => (this.statements = data),
      error: () => undefined
    });
  }

  get projectsCount(): number {
    return this.filteredProjects.length;
  }

  get activeProjectsCount(): number {
    return this.filteredProjects.filter((project) => project.status === 'IN_PROGRESS').length;
  }

  get plannedProjectsCount(): number {
    return this.filteredProjects.filter((project) => project.status === 'PLANNED').length;
  }

  get completedProjectsCount(): number {
    return this.filteredProjects.filter((project) => project.status === 'COMPLETED').length;
  }

  get filteredProjects(): Project[] {
    if (!this.selectedProjectId) return this.projects;
    return this.projects.filter((project) => project.id === this.selectedProjectId);
  }

  get filteredExpensesSource(): Expense[] {
    if (!this.selectedProjectId) return this.expenses;
    return this.expenses.filter((expense) => expense.projectId === this.selectedProjectId);
  }

  get filteredPurchasesSource(): ClientPurchase[] {
    if (!this.selectedProjectId) return this.purchases;
    return this.purchases.filter((purchase) => purchase.projectId === this.selectedProjectId);
  }

  get filteredAdvancesSource(): ClientAdvance[] {
    if (!this.selectedProjectId) return this.advances;
    return this.advances.filter((advance) => advance.projectId === this.selectedProjectId);
  }

  get totalCashIn(): number {
    const advancesAmount = this.filteredAdvancesSource.reduce((sum, item) => sum + (item.amount ?? 0), 0);
    const directPaymentsAmount = this.filteredPurchasesSource.reduce((sum, item) => sum + (item.paidAmount ?? 0), 0);
    return advancesAmount + directPaymentsAmount;
  }

  get totalCashOut(): number {
    return this.filteredExpensesSource.reduce((sum, item) => sum + (item.amountTtc ?? 0), 0);
  }

  get balance(): number {
    return this.totalCashIn - this.totalCashOut;
  }

  get totalReceivables(): number {
    return this.filteredPurchasesSource.reduce((sum, item) => sum + (item.totalAmount ?? 0), 0) - this.totalCashIn;
  }

  get totalBudget(): number {
    return this.filteredProjects.reduce((sum, project) => sum + (project.budget ?? 0), 0);
  }

  get budgetConsumptionRate(): number {
    if (!this.totalBudget) return 0;
    return Math.min(100, Math.round((this.totalCashOut / this.totalBudget) * 100));
  }

  get estimatedMargin(): number {
    return this.filteredPurchasesSource.reduce((sum, item) => sum + (item.totalAmount ?? 0), 0) - this.totalCashOut;
  }

  get bestProjects(): { name: string; margin: number }[] {
    return this.projects
      .filter((project) => !this.selectedProjectId || project.id === this.selectedProjectId)
      .map((project) => {
        const id = project.id;
        const purchases = this.purchases
          .filter((item) => item.projectId === id)
          .reduce((sum, item) => sum + (item.totalAmount ?? 0), 0);
        const expenses = this.expenses
          .filter((item) => item.projectId === id)
          .reduce((sum, item) => sum + (item.amountTtc ?? 0), 0);
        return { name: project.name, margin: purchases - expenses };
      })
      .sort((a, b) => b.margin - a.margin)
      .slice(0, 3);
  }

  get collectionRate(): number {
    const purchases = this.filteredPurchasesSource.reduce((sum, item) => sum + (item.totalAmount ?? 0), 0);
    const advances = this.totalCashIn;
    if (!purchases) return 0;
    return Math.min(100, Math.round((advances / purchases) * 100));
  }

  get topDebtors(): ClientStatement[] {
    const allowedClientIds = new Set(
      this.filteredPurchasesSource.map((item) => item.clientId).filter((id): id is number => !!id)
    );
    return [...this.statements]
      .filter((item) => !this.selectedProjectId || allowedClientIds.has(item.clientId))
      .sort((a, b) => (b.remainingToPay ?? 0) - (a.remainingToPay ?? 0))
      .slice(0, 5);
  }

  get recentExpenses(): Expense[] {
    return [...this.filteredExpensesSource]
      .sort((a, b) => new Date(b.expenseDate).getTime() - new Date(a.expenseDate).getTime())
      .slice(0, 5);
  }

  get expenseTrend(): { label: string; amount: number; width: number }[] {
    const totals = new Map<string, number>();
    for (const expense of this.filteredExpensesSource) {
      const date = new Date(expense.expenseDate);
      if (Number.isNaN(date.getTime())) continue;
      const label = date.toLocaleDateString('fr-FR', { month: 'short', year: '2-digit' });
      totals.set(label, (totals.get(label) ?? 0) + (expense.amountTtc ?? 0));
    }

    const entries = Array.from(totals.entries())
      .slice(-6)
      .map(([label, amount]) => ({ label, amount, width: 0 }));
    const max = Math.max(...entries.map((entry) => entry.amount), 0);

    return entries.map((entry) => ({
      ...entry,
      width: max > 0 ? Math.max(8, Math.round((entry.amount / max) * 100)) : 0
    }));
  }

  getProjectStatusLabel(status?: string): string {
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

  getProjectSeverity(status?: string): 'success' | 'info' | 'warning' | 'danger' | 'secondary' | 'contrast' {
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

  getProjectBudget(project: Project): number {
    return project.budget ?? 0;
  }

  getProjectBudgetRemaining(project: Project): number {
    return Math.max(0, this.getProjectBudget(project) - this.getProjectExpenseAmount(project));
  }

  getProjectBudgetUsage(project: Project): number {
    if (!project.id || !project.budget || project.budget <= 0) return 0;
    const totalExpense = this.expenses
      .filter((expense) => expense.projectId === project.id)
      .reduce((sum, expense) => sum + (expense.amountTtc ?? 0), 0);
    return Math.min(100, Math.round((totalExpense / project.budget) * 100));
  }

  getProjectExpenseAmount(project: Project): number {
    if (!project.id) return 0;
    return this.expenses
      .filter((expense) => expense.projectId === project.id)
      .reduce((sum, expense) => sum + (expense.amountTtc ?? 0), 0);
  }
}
