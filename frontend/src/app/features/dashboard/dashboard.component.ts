import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CardModule } from 'primeng/card';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { ApiService } from '../../core/services/api.service';
import { ProjectContextService } from '../../core/services/project-context.service';
import {
  AmountByLabel,
  ClientStatement,
  DashboardSummary,
  Expense,
  Project,
  ReportScopeParams
} from '../../shared/models/models';

/** Expenses listed under "dernières dépenses". */
const RECENT_EXPENSES = 5;

/** Months shown in the trend bars. */
const TREND_MONTHS = 6;

/** Projects listed under "meilleures marges". */
const TOP_PROJECTS = 3;

/** Clients listed under "principaux débiteurs". */
const TOP_DEBTORS = 5;

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, CardModule, ButtonModule, ProgressSpinnerModule, TableModule, TagModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DashboardComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly projectContext = inject(ProjectContextService);
  private readonly destroyRef = inject(DestroyRef);
  /** The selected project as a stream, so the reaction can be released on destroy. */
  private readonly selectedProjectId$ = toObservable(this.projectContext.selectedProjectId);

  /** Signals throughout, so the view refreshes under OnPush when a response lands (PERF-04). */
  readonly loading = signal(true);
  readonly summary = signal<DashboardSummary | undefined>(undefined);
  readonly statements = signal<ClientStatement[]>([]);
  readonly projects = signal<Project[]>([]);
  readonly recentExpenses = signal<Expense[]>([]);
  readonly selectedProjectId = signal<number | null>(null);

  /** Expense totals per project, keyed by project name, as aggregated by the backend. */
  private readonly expensesByProject = signal<Map<string, number>>(new Map());
  /** Contracted totals per project, keyed by project name. */
  private readonly purchasesByProject = signal<Map<string, number>>(new Map());
  private readonly monthlyExpenses = signal<AmountByLabel[]>([]);

  ngOnInit(): void {
    // The projects list is small and bounded, and feeds the status counts and the budget cards.
    this.api.getProjects().pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (data) => this.projects.set(data) });

    this.selectedProjectId$.pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((projectId) => {
        this.selectedProjectId.set(projectId);
        this.load();
      });
  }

  /**
   * Everything on this screen comes from an aggregate scoped to the selected project, plus one
   * short page of recent expenses. The dashboard used to load every expense, contract and
   * advance and reduce them in the browser (PERF-02).
   */
  private load(): void {
    const scope: ReportScopeParams = { projectId: this.selectedProjectId() };
    let pending = 6;
    const done = () => {
      pending -= 1;
      if (pending <= 0) {
        this.loading.set(false);
      }
    };

    this.api.getDashboardSummary(scope).subscribe({
      next: (data) => this.summary.set(data),
      error: done,
      complete: done
    });

    this.api.getClientStatements(scope).subscribe({
      next: (data) => this.statements.set(data),
      error: done,
      complete: done
    });

    this.api.getExpensesByProject(scope).subscribe({
      next: (data) => (this.expensesByProject.set(DashboardComponent.byLabel(data))),
      error: done,
      complete: done
    });

    this.api.getPurchasesByProject(scope).subscribe({
      next: (data) => (this.purchasesByProject.set(DashboardComponent.byLabel(data))),
      error: done,
      complete: done
    });

    this.api.getExpensesByMonth(scope).subscribe({
      next: (data) => this.monthlyExpenses.set(data),
      error: done,
      complete: done
    });

    this.api
      .getExpenses({ projectId: this.selectedProjectId() }, { page: 0, size: RECENT_EXPENSES, sort: 'expenseDate,desc' })
      .subscribe({
        next: (data) => this.recentExpenses.set(data.content),
        error: done,
        complete: done
      });
  }

  private static byLabel(rows: AmountByLabel[]): Map<string, number> {
    return new Map(rows.map((row) => [row.label, row.amount ?? 0]));
  }

  get filteredProjects(): Project[] {
    if (!this.selectedProjectId()) return this.projects();
    return this.projects().filter((project) => project.id === this.selectedProjectId());
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

  /** Business documents recorded in scope, counted by the backend. */
  get documentCount(): number {
    return (this.summary()?.expenses ?? 0) + (this.summary()?.clientPurchases ?? 0)
      + (this.summary()?.clientAdvances ?? 0);
  }

  get totalCashIn(): number {
    return this.summary()?.totalAdvances ?? 0;
  }

  get totalCashOut(): number {
    return this.summary()?.totalExpenses ?? 0;
  }

  get balance(): number {
    return this.totalCashIn - this.totalCashOut;
  }

  get totalReceivables(): number {
    return this.summary()?.totalRemainingFromClients ?? 0;
  }

  get totalBudget(): number {
    return this.filteredProjects.reduce((sum, project) => sum + (project.budget ?? 0), 0);
  }

  get budgetConsumptionRate(): number {
    if (!this.totalBudget) return 0;
    return Math.min(100, Math.round((this.totalCashOut / this.totalBudget) * 100));
  }

  get estimatedMargin(): number {
    return (this.summary()?.totalPurchases ?? 0) - this.totalCashOut;
  }

  get collectionRate(): number {
    const contracted = this.summary()?.totalPurchases ?? 0;
    if (!contracted) return 0;
    return Math.min(100, Math.round((this.totalCashIn / contracted) * 100));
  }

  get bestProjects(): { name: string; margin: number }[] {
    return this.filteredProjects
      .map((project) => ({
        name: project.name,
        margin: (this.purchasesByProject().get(project.name) ?? 0) - (this.expensesByProject().get(project.name) ?? 0)
      }))
      .sort((a, b) => b.margin - a.margin)
      .slice(0, TOP_PROJECTS);
  }

  get topDebtors(): ClientStatement[] {
    // The statements are already scoped to the selected project by the backend.
    return [...this.statements()]
      .sort((a, b) => (b.remainingToPay ?? 0) - (a.remainingToPay ?? 0))
      .slice(0, TOP_DEBTORS);
  }

  get expenseTrend(): { label: string; amount: number; width: number }[] {
    const entries = this.monthlyExpenses().slice(-TREND_MONTHS).map((row) => ({
      label: DashboardComponent.monthLabel(row.label),
      amount: row.amount ?? 0,
      width: 0
    }));
    const max = Math.max(...entries.map((entry) => entry.amount), 0);

    return entries.map((entry) => ({
      ...entry,
      width: max > 0 ? Math.max(8, Math.round((entry.amount / max) * 100)) : 0
    }));
  }

  /** Turns the backend's `2026-09` into a short French month label. */
  private static monthLabel(label: string): string {
    const [year, month] = label.split('-');
    const date = new Date(Number(year), Number(month) - 1, 1);
    return Number.isNaN(date.getTime())
      ? label
      : date.toLocaleDateString('fr-FR', { month: 'short', year: '2-digit' });
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

  getProjectExpenseAmount(project: Project): number {
    return this.expensesByProject().get(project.name) ?? 0;
  }

  getProjectBudgetRemaining(project: Project): number {
    return Math.max(0, this.getProjectBudget(project) - this.getProjectExpenseAmount(project));
  }

  getProjectBudgetUsage(project: Project): number {
    const budget = this.getProjectBudget(project);
    if (budget <= 0) return 0;
    return Math.min(100, Math.round((this.getProjectExpenseAmount(project) / budget) * 100));
  }
}
