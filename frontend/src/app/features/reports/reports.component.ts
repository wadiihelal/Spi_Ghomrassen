import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { ApiService } from '../../core/services/api.service';
import { AmountByLabel, ClientStatement } from '../../shared/models/models';

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [CommonModule, FormsModule, CardModule, TableModule, ButtonModule],
  templateUrl: './reports.component.html',
  styleUrl: './reports.component.css'
})
export class ReportsComponent implements OnInit {
  private readonly api = inject(ApiService);

  byCategory: AmountByLabel[] = [];
  byProject: AmountByLabel[] = [];
  clientStatements: ClientStatement[] = [];
  selectedYear = new Date().getFullYear();
  selectedMonth = new Date().getMonth() + 1;
  exportingExcel = false;
  exportingPdf = false;

  ngOnInit(): void {
    this.loadReports();
  }

  loadReports(): void {
    this.api.getExpensesByCategory().subscribe((data) => (this.byCategory = data));
    this.api.getExpensesByProject().subscribe((data) => (this.byProject = data));
    this.api.getClientStatements().subscribe((data) => (this.clientStatements = data));
  }

  exportExcel(): void {
    this.exportingExcel = true;
    this.api.downloadReportsExcel(this.selectedYear, this.selectedMonth).subscribe({
      next: (blob) => {
        this.saveFile(blob, `rapport-spi-ghomrassen-${this.selectedYear}-${String(this.selectedMonth).padStart(2, '0')}.xlsx`);
        this.exportingExcel = false;
      },
      error: () => {
        this.exportingExcel = false;
      }
    });
  }

  exportPdf(): void {
    this.exportingPdf = true;
    this.api.downloadReportsPdf(this.selectedYear, this.selectedMonth).subscribe({
      next: (blob) => {
        this.saveFile(blob, `rapport-spi-ghomrassen-${this.selectedYear}-${String(this.selectedMonth).padStart(2, '0')}.pdf`);
        this.exportingPdf = false;
      },
      error: () => {
        this.exportingPdf = false;
      }
    });
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
