import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { ApiService } from '../../core/services/api.service';
import { ProjectContextService } from '../../core/services/project-context.service';
import { AmountByLabel, ClientStatement, Project, ReportScopeParams } from '../../shared/models/models';

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [CommonModule, FormsModule, CardModule, TableModule, ButtonModule, CheckboxModule],
  templateUrl: './reports.component.html',
  styleUrl: './reports.component.css'
})
export class ReportsComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly projectContext = inject(ProjectContextService);

  byCategory: AmountByLabel[] = [];
  byProject: AmountByLabel[] = [];
  clientStatements: ClientStatement[] = [];
  projects: Project[] = [];
  selectedProjectId: number | null = null;
  selectedYear = new Date().getFullYear();
  selectedMonth: number | null = new Date().getMonth() + 1;
  /** Aggregate across every project instead of the one selected in the header. */
  allProjects = false;
  exportingExcel = false;
  exportingPdf = false;

  ngOnInit(): void {
    this.api.getProjects().subscribe((data) => (this.projects = data));
    this.projectContext.selectedProjectId$.subscribe((projectId) => {
      this.selectedProjectId = projectId;
      this.loadReports();
    });
  }

  /** Scope sent to every report call: the header's project unless "tous les projets" is on. */
  get scope(): ReportScopeParams {
    return {
      projectId: this.allProjects ? 'ALL' : this.selectedProjectId,
      year: this.selectedYear,
      month: this.selectedMonth
    };
  }

  get scopeLabel(): string {
    const projectName = this.allProjects
      ? 'Tous les projets'
      : this.projects.find((project) => project.id === this.selectedProjectId)?.name ?? 'Tous les projets';
    const period = this.selectedMonth
      ? `${String(this.selectedMonth).padStart(2, '0')}/${this.selectedYear}`
      : `année ${this.selectedYear}`;
    return `${projectName} — ${period}`;
  }

  loadReports(): void {
    const scope = this.scope;
    this.api.getExpensesByCategory(scope).subscribe((data) => (this.byCategory = data));
    this.api.getExpensesByProject(scope).subscribe((data) => (this.byProject = data));
    this.api.getClientStatements(scope).subscribe((data) => (this.clientStatements = data));
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
