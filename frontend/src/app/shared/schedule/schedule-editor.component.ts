import { ChangeDetectionStrategy, Component, OnChanges, SimpleChanges, computed, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { ApiService } from '../../core/services/api.service';
import { UiService } from '../../core/services/ui.service';
import { DinarPipe } from '../pipes/dinar.pipe';
import { InstallmentLine, InstallmentStatus, PaymentSchedule } from '../models/models';

/** The usual off-plan split: a deposit, two construction tranches, the balance at handover. */
const DEFAULT_PERCENTAGES = [20, 30, 30, 20];

/** One editable row of the plan being composed. */
interface EditableLine {
  label: string;
  dueDate: string;
  amount: number;
}

/**
 * Composes and edits a contract's payment schedule (UX-03).
 *
 * <p>Generating from percentages covers the common case in one click; the resulting lines stay
 * editable because real contracts get negotiated. The backend refuses a plan that does not add
 * up to the contract, and this panel shows the gap before the user tries to save.</p>
 */
@Component({
  selector: 'app-schedule-editor',
  standalone: true,
  imports: [CommonModule, FormsModule, ButtonModule, TableModule, TagModule, InputTextModule,
    InputNumberModule, DinarPipe],
  templateUrl: './schedule-editor.component.html',
  styleUrl: './schedule-editor.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ScheduleEditorComponent implements OnChanges {
  private readonly api = inject(ApiService);
  private readonly ui = inject(UiService);

  /** Null while the parent form is creating the contract: there is nothing to plan against yet. */
  readonly purchaseId = input<number | null>(null);
  readonly contractAmount = input<number>(0);

  /** Raised after any change, so the parent can refresh its own totals. */
  readonly changed = output<void>();

  readonly schedule = signal<PaymentSchedule | undefined>(undefined);
  readonly lines = signal<EditableLine[]>([]);
  readonly busy = signal(false);

  /** Template controls. */
  firstDueDate = ScheduleEditorComponent.today();
  intervalMonths = 3;
  percentages: number[] = [...DEFAULT_PERCENTAGES];

  readonly percentageTotal = computed(() => this.percentages.reduce((sum, value) => sum + (value || 0), 0));

  readonly linesTotal = computed(() => this.lines().reduce((sum, line) => sum + (line.amount || 0), 0));

  /** What the plan still has to cover before the backend will accept it. */
  readonly gap = computed(() => Number((this.contractAmount() - this.linesTotal()).toFixed(3)));

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['purchaseId']) {
      this.reload();
    }
  }

  reload(): void {
    const purchaseId = this.purchaseId();
    if (!purchaseId) {
      this.schedule.set(undefined);
      this.lines.set([]);
      return;
    }
    this.api.getSchedule(purchaseId).subscribe({
      next: (data) => {
        this.schedule.set(data);
        this.lines.set(data.installments.map((line) => ({
          label: line.label,
          dueDate: line.dueDate,
          amount: line.amount
        })));
      }
    });
  }

  generate(): void {
    const purchaseId = this.purchaseId();
    if (!purchaseId) return;
    if (Math.abs(this.percentageTotal() - 100) > 0.001) {
      this.ui.info('Pourcentages incomplets',
        `Le modèle totalise ${this.percentageTotal()} % au lieu de 100 %.`);
      return;
    }

    this.busy.set(true);
    this.api.generateSchedule(purchaseId, {
      firstDueDate: this.firstDueDate,
      intervalMonths: this.intervalMonths,
      lines: this.percentages.map((percentage) => ({ percentage }))
    }).subscribe({
      next: (data) => {
        this.applySchedule(data);
        this.ui.success('Échéancier généré', `${data.installments.length} échéances créées.`);
      },
      error: () => this.busy.set(false)
    });
  }

  save(): void {
    const purchaseId = this.purchaseId();
    if (!purchaseId) return;
    const payload: InstallmentLine[] = this.lines().map((line) => ({
      label: line.label,
      dueDate: line.dueDate,
      amount: line.amount
    }));

    this.busy.set(true);
    this.api.saveSchedule(purchaseId, payload).subscribe({
      next: (data) => {
        this.applySchedule(data);
        this.ui.success('Échéancier enregistré', `${data.installments.length} échéances.`);
      },
      // The interceptor surfaces the backend's message, including the plan/contract mismatch.
      error: () => this.busy.set(false)
    });
  }

  clear(): void {
    const purchaseId = this.purchaseId();
    if (!purchaseId) return;
    this.ui.confirmDelete('Supprimer l’échéancier de ce contrat ? Les encaissements sont conservés.', () => {
      this.api.clearSchedule(purchaseId).subscribe({
        next: () => {
          this.reload();
          this.changed.emit();
          this.ui.success('Échéancier supprimé', 'Le plan de paiement a été retiré.');
        }
      });
    });
  }

  private applySchedule(data: PaymentSchedule): void {
    this.schedule.set(data);
    this.lines.set(data.installments.map((line) => ({
      label: line.label,
      dueDate: line.dueDate,
      amount: line.amount
    })));
    this.busy.set(false);
    this.changed.emit();
  }

  addLine(): void {
    const last = this.lines()[this.lines().length - 1];
    const nextDate = last ? ScheduleEditorComponent.addMonths(last.dueDate, this.intervalMonths) : this.firstDueDate;
    this.lines.update((current) => [...current, {
      label: `Tranche ${current.length}`,
      dueDate: nextDate,
      amount: Math.max(0, this.gap())
    }]);
  }

  removeLine(index: number): void {
    this.lines.update((current) => current.filter((_, position) => position !== index));
  }

  /** Puts whatever is missing on the last line, so the plan matches the contract. */
  balanceOnLastLine(): void {
    const gap = this.gap();
    if (!this.lines().length || gap === 0) return;
    this.lines.update((current) => current.map((line, index) =>
      index === current.length - 1
        ? { ...line, amount: Number((line.amount + gap).toFixed(3)) }
        : line));
  }

  addPercentage(): void { this.percentages = [...this.percentages, 0]; }
  removePercentage(index: number): void {
    this.percentages = this.percentages.filter((_, position) => position !== index);
  }
  trackByIndex(index: number): number { return index; }

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

  private static today(): string {
    return new Date().toISOString().slice(0, 10);
  }

  private static addMonths(iso: string, months: number): string {
    const date = new Date(iso);
    date.setMonth(date.getMonth() + months);
    return date.toISOString().slice(0, 10);
  }
}
