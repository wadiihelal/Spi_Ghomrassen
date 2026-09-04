import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { TagModule } from 'primeng/tag';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApiService } from '../../core/services/api.service';
import { UiService } from '../../core/services/ui.service';
import { ProjectContextService } from '../../core/services/project-context.service';
import { DinarPipe } from '../../shared/pipes/dinar.pipe';
import { SalesBoard, SalesBoardUnit, SalesStatus } from '../../shared/models/models';

/** The four commercial states, in the order a unit moves through them. */
const STATUS_META: { status: SalesStatus; label: string; tone: string }[] = [
  { status: 'AVAILABLE', label: 'Disponible', tone: 'available' },
  { status: 'RESERVED', label: 'Réservé', tone: 'reserved' },
  { status: 'SOLD', label: 'Vendu', tone: 'sold' },
  { status: 'DELIVERED', label: 'Livré', tone: 'delivered' }
];

/**
 * Plan de commercialisation (UX-05).
 *
 * <p>The whole stock of the selected project, block by block and floor by floor, so the promoter
 * sees at a glance what is left to sell. Clicking a unit opens what it is worth and lets its
 * state be changed; the backend refuses any state its contract contradicts.</p>
 */
@Component({
  selector: 'app-sales-board',
  standalone: true,
  imports: [CommonModule, CardModule, ButtonModule, DialogModule, TagModule, ProgressSpinnerModule, DinarPipe],
  templateUrl: './sales-board.component.html',
  styleUrl: './sales-board.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SalesBoardComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly ui = inject(UiService);
  private readonly projectContext = inject(ProjectContextService);
  private readonly destroyRef = inject(DestroyRef);

  readonly board = signal<SalesBoard | undefined>(undefined);
  readonly loading = signal(true);
  readonly selectedUnit = signal<SalesBoardUnit | null>(null);
  readonly statusMeta = STATUS_META;
  unitDialogVisible = false;

  /** Share of the stock already placed, for the headline gauge. */
  readonly placedShare = computed(() => {
    const board = this.board();
    if (!board || !board.unitCount) return 0;
    return Math.round(((board.unitCount - board.availableCount) / board.unitCount) * 100);
  });

  /** Share of the contracted money already collected. */
  readonly collectedShare = computed(() => {
    const board = this.board();
    if (!board || !board.contractedAmount) return 0;
    return Math.round((board.collectedAmount / board.contractedAmount) * 100);
  });

  ngOnInit(): void {
    this.projectContext.scope$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.load());
  }

  private load(): void {
    this.loading.set(true);
    this.api.getSalesBoard(this.projectContext.selectedProjectId()).subscribe({
      next: (data) => {
        this.board.set(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  openUnit(unit: SalesBoardUnit): void {
    this.selectedUnit.set(unit);
    this.unitDialogVisible = true;
  }

  /** Moves the selected unit to another state, then reloads the board's totals. */
  changeStatus(status: SalesStatus): void {
    const unit = this.selectedUnit();
    if (!unit || unit.salesStatus === status) return;

    this.api.changeSalesStatus(unit.id, status).subscribe({
      next: () => {
        this.unitDialogVisible = false;
        this.selectedUnit.set(null);
        this.load();
        this.ui.success('Statut mis à jour', `${unit.apartmentNumber} — ${this.statusLabel(status)}.`);
      }
      // A state the contract contradicts is refused by the backend and shown by the interceptor.
    });
  }

  statusLabel(status?: SalesStatus): string {
    return STATUS_META.find((meta) => meta.status === status)?.label ?? 'Disponible';
  }

  statusTone(status?: SalesStatus): string {
    return STATUS_META.find((meta) => meta.status === status)?.tone ?? 'available';
  }

  /** Percentage of one unit's contract already collected, for the dialog's bar. */
  unitCollectedShare(unit: SalesBoardUnit): number {
    if (!unit.contractedAmount) return 0;
    return Math.min(100, Math.round((unit.collectedAmount / unit.contractedAmount) * 100));
  }

  countFor(status: SalesStatus): number {
    const board = this.board();
    if (!board) return 0;
    switch (status) {
      case 'AVAILABLE': return board.availableCount;
      case 'RESERVED': return board.reservedCount;
      case 'SOLD': return board.soldCount;
      default: return board.deliveredCount;
    }
  }
}
