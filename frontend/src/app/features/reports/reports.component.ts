import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { DropdownModule } from 'primeng/dropdown';
import { ApiService } from '../../core/services/api.service';
import { DinarPipe } from '../../shared/pipes/dinar.pipe';
import { ProjectContextService } from '../../core/services/project-context.service';
import { AmountByLabel, ClientStatement, Project, ReportScopeParams } from '../../shared/models/models';

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [CommonModule, FormsModule, CardModule, TableModule, ButtonModule, CheckboxModule,
    DropdownModule, DinarPipe],
  templateUrl: './reports.component.html',
  styleUrl: './reports.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ReportsComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly projectContext = inject(ProjectContextService);
  private readonly destroyRef = inject(DestroyRef);

  readonly byCategory = signal<AmountByLabel[]>([]);
  readonly byProject = signal<AmountByLabel[]>([]);
  readonly clientStatements = signal<ClientStatement[]>([]);
  readonly projects = signal<Project[]>([]);
  readonly selectedProjectId = signal<number | null>(null);
  selectedYear = new Date().getFullYear();
  /** The screen opens on the current month; « Toute l'année » widens it to the whole year. */
  selectedMonth: number | null = new Date().getMonth() + 1;
  /** Five years back, plus the current one: enough for a promoter's archive. */
  readonly years = ReportsComponent.recentYears();
  readonly months = ReportsComponent.frenchMonths();
  /** Aggregate across every project instead of the one selected in the header. */
  allProjects = false;
  exportingExcel = false;
  exportingPdf = false;

  ngOnInit(): void {
    this.api.getProjects().subscribe((data) => this.projects.set(data));
    this.projectContext.scope$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((projectId) => {
      this.selectedProjectId.set(projectId);
      this.loadReports();
    });
  }

  /** Totals of the period on show, so the aggregates below have a headline. */
  readonly totalExpenses = computed(() =>
    this.byCategory().reduce((sum, row) => sum + (row.amount ?? 0), 0));

  readonly totalContracted = computed(() =>
    this.clientStatements().reduce((sum, row) => sum + (row.totalPurchases ?? 0), 0));

  readonly totalCollected = computed(() =>
    this.clientStatements().reduce((sum, row) => sum + (row.totalAdvances ?? 0), 0));

  readonly totalRemaining = computed(() =>
    this.clientStatements().reduce((sum, row) => sum + (row.remainingToPay ?? 0), 0));

  /** Scope sent to every report call: the header's project unless "tous les projets" is on. */
  get scope(): ReportScopeParams {
    return {
      projectId: this.allProjects ? 'ALL' : this.selectedProjectId(),
      year: this.selectedYear,
      month: this.selectedMonth
    };
  }

  get scopeLabel(): string {
    const projectName = this.allProjects
      ? 'Tous les projets'
      : this.projects().find((project) => project.id === this.selectedProjectId())?.name ?? 'Tous les projets';
    const period = this.selectedMonth
      ? `${String(this.selectedMonth).padStart(2, '0')}/${this.selectedYear}`
      : `année ${this.selectedYear}`;
    return `${projectName} — ${period}`;
  }

  loadReports(): void {
    const scope = this.scope;
    this.api.getExpensesByCategory(scope).subscribe((data) => this.byCategory.set(data));
    this.api.getExpensesByProject(scope).subscribe((data) => this.byProject.set(data));
    this.api.getClientStatements(scope).subscribe((data) => this.clientStatements.set(data));
  }

  /** The month's VAT recap, on the same period and scope as the figures shown (UX-06). */
  get vatSummaryUrl(): string {
    const month = this.selectedMonth ?? new Date().getMonth() + 1;
    // « Tous les projets » means no project narrowing, the same as on the figures above.
    const projectId = this.allProjects ? null : this.selectedProjectId();
    return this.api.vatSummaryUrl(this.selectedYear, month, projectId);
  }

  exportExcel(): void {
    this.exportingExcel = true;
    this.api.downloadReportsExcel(this.scope).subscribe({
      next: (blob) => {
        this.saveFile(blob, this.fileName('xlsx'));
        this.exportingExcel = false;
      },
      error: () => {
        this.exportingExcel = false;
      }
    });
  }

  exportPdf(): void {
    this.exportingPdf = true;
    this.api.downloadReportsPdf(this.scope).subscribe({
      next: (blob) => {
        this.saveFile(blob, this.fileName('pdf'));
        this.exportingPdf = false;
      },
      error: () => {
        this.exportingPdf = false;
      }
    });
  }

  private static recentYears(): { label: string; value: number }[] {
    const current = new Date().getFullYear();
    return Array.from({ length: 6 }, (unused, index) => current - index)
      .map((year) => ({ label: String(year), value: year }));
  }

  private static frenchMonths(): { label: string; value: number | null }[] {
    const months = Array.from({ length: 12 }, (unused, index) => {
      const label = new Date(2000, index, 1).toLocaleDateString('fr-FR', { month: 'long' });
      return { label: label.charAt(0).toUpperCase() + label.slice(1), value: index + 1 };
    });
    return [{ label: "Toute l'année", value: null }, ...months];
  }

  private fileName(extension: string): string {
    const month = String(this.selectedMonth ?? 1).padStart(2, '0');
    return `rapport-spi-ghomrassen-${this.selectedYear}-${month}.${extension}`;
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
