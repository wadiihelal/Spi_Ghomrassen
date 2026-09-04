import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { DropdownModule } from 'primeng/dropdown';
import { InputTextModule } from 'primeng/inputtext';
import { ApiService } from '../../core/services/api.service';
import { ProjectContextService } from '../../core/services/project-context.service';
import { DinarPipe } from '../../shared/pipes/dinar.pipe';
import { Client, InstallmentStatus, InstallmentSummary, PaymentInstallment } from '../../shared/models/models';

/** Status filter options, in the order a promoter cares about them. */
const STATUS_OPTIONS: { label: string; value: InstallmentStatus | '' }[] = [
  { label: 'Tous les statuts', value: '' },
  { label: 'En retard', value: 'OVERDUE' },
  { label: 'À venir', value: 'UPCOMING' },
  { label: 'Partielle', value: 'PARTIALLY_PAID' },
  { label: 'Payée', value: 'PAID' }
];

/**
 * Échéancier across every contract of the selected project (UX-03).
 *
 * <p>Answers the two questions a promoter asks each morning: who is late, and who owes money
 * this month. The rows come from the backend with their settlement already resolved.</p>
 */
@Component({
  selector: 'app-schedules',
  standalone: true,
  imports: [CommonModule, FormsModule, CardModule, TableModule, TagModule, ButtonModule,
    DropdownModule, InputTextModule, DinarPipe],
  templateUrl: './schedules.component.html',
  styleUrl: './schedules.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SchedulesComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly projectContext = inject(ProjectContextService);
  private readonly destroyRef = inject(DestroyRef);

  readonly installments = signal<PaymentInstallment[]>([]);
  readonly summary = signal<InstallmentSummary | undefined>(undefined);
  readonly clients = signal<Client[]>([]);
  readonly loading = signal(false);
  readonly selectedProjectId = signal<number | null>(null);

  readonly statusOptions = STATUS_OPTIONS;

  filters: { status: InstallmentStatus | ''; clientId: number | null; dueFrom: string; dueTo: string } = {
    status: '',
    clientId: null,
    dueFrom: '',
    dueTo: ''
  };

  /** Total still owed across the rows on screen. */
  readonly displayedRemaining = computed(() =>
    this.installments().reduce((sum, line) => sum + (line.remainingAmount ?? 0), 0));

  ngOnInit(): void {
    this.projectContext.scope$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((projectId) => {
      this.selectedProjectId.set(projectId);
      this.api.getClients(projectId).subscribe({ next: (data) => this.clients.set(data) });
      this.reload();
    });
  }

  reload(): void {
    this.loading.set(true);
    this.api.getInstallments({
      projectId: this.selectedProjectId(),
      clientId: this.filters.clientId,
      status: this.filters.status || null,
      dueFrom: this.filters.dueFrom || null,
      dueTo: this.filters.dueTo || null
    }).subscribe({
      next: (data) => {
        this.installments.set(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
    this.api.getInstallmentSummary(this.selectedProjectId())
      .subscribe({ next: (data) => this.summary.set(data) });
  }

  /** Shows only what is late, the view a promoter opens first. */
  showOverdueOnly(): void {
    this.filters = { status: 'OVERDUE', clientId: null, dueFrom: '', dueTo: '' };
    this.reload();
  }

  /** Shows the instalments falling due in the current month. */
  showThisMonth(): void {
    const today = new Date();
    const first = new Date(today.getFullYear(), today.getMonth(), 1);
    const last = new Date(today.getFullYear(), today.getMonth() + 1, 0);
    this.filters = {
      status: '',
      clientId: null,
      dueFrom: SchedulesComponent.isoDate(first),
      dueTo: SchedulesComponent.isoDate(last)
    };
    this.reload();
  }

  resetFilters(): void {
    this.filters = { status: '', clientId: null, dueFrom: '', dueTo: '' };
    this.reload();
  }

  private static isoDate(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }

  statusLabel(status: InstallmentStatus): string {
    switch (status) {
      case 'PAID': return 'Payée';
      case 'PARTIALLY_PAID': return 'Partielle';
      case 'OVERDUE': return 'En retard';
      default: return 'À venir';
    }
  }

  statusSeverity(status: InstallmentStatus): 'success' | 'warning' | 'danger' | 'secondary' {
    switch (status) {
      case 'PAID': return 'success';
      case 'PARTIALLY_PAID': return 'warning';
      case 'OVERDUE': return 'danger';
      default: return 'secondary';
    }
  }

  /** "12 jours" for a late line, blank otherwise. */
  latenessLabel(line: PaymentInstallment): string {
    return line.daysLate > 0 ? `${line.daysLate} j de retard` : '';
  }
}
