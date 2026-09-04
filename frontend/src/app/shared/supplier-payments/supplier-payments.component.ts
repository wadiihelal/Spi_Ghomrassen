import { ChangeDetectionStrategy, Component, OnChanges, SimpleChanges, computed, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { DropdownModule } from 'primeng/dropdown';
import { ApiService } from '../../core/services/api.service';
import { UiService } from '../../core/services/ui.service';
import { DinarPipe } from '../pipes/dinar.pipe';
import { SupplierPayment } from '../models/models';

/** Payment methods a promoter actually uses with suppliers. */
const PAYMENT_METHODS = [
  { label: 'Virement bancaire', value: 'BANK_TRANSFER' },
  { label: 'Chèque', value: 'CHECK' },
  { label: 'Espèces', value: 'CASH' },
  { label: 'Carte', value: 'CARD' },
  { label: 'Autre', value: 'OTHER' }
];

/**
 * Payments recorded against one supplier invoice (UX-04).
 *
 * <p>The invoice's state is the sum of these lines, so adding or removing one immediately
 * changes what the promoter owes. The backend refuses a payment that would take the invoice
 * past its gross amount.</p>
 */
@Component({
  selector: 'app-supplier-payments',
  standalone: true,
  imports: [CommonModule, FormsModule, ButtonModule, InputTextModule, InputNumberModule,
    DropdownModule, DinarPipe],
  templateUrl: './supplier-payments.component.html',
  styleUrl: './supplier-payments.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SupplierPaymentsComponent implements OnChanges {
  private readonly api = inject(ApiService);
  private readonly ui = inject(UiService);

  readonly invoiceId = input<number | null>(null);
  readonly grossAmount = input<number>(0);

  /** Raised after a change so the parent can refresh its totals. */
  readonly changed = output<void>();

  readonly payments = signal<SupplierPayment[]>([]);
  readonly busy = signal(false);
  readonly methods = PAYMENT_METHODS;

  readonly paidTotal = computed(() =>
    this.payments().reduce((sum, payment) => sum + (payment.amount ?? 0), 0));

  readonly remaining = computed(() =>
    Number((this.grossAmount() - this.paidTotal()).toFixed(3)));

  /** The form for a new payment, pre-filled with whatever is still owed. */
  draft: SupplierPayment = SupplierPaymentsComponent.emptyDraft();

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['invoiceId']) {
      this.reload();
    }
  }

  reload(): void {
    const invoiceId = this.invoiceId();
    if (!invoiceId) {
      this.payments.set([]);
      return;
    }
    this.api.getSupplierPayments(invoiceId).subscribe({
      next: (data) => {
        this.payments.set(data);
        this.resetDraft();
      }
    });
  }

  add(): void {
    const invoiceId = this.invoiceId();
    if (!invoiceId || !this.draft.amount || this.draft.amount <= 0) {
      this.ui.info('Montant manquant', 'Saisissez le montant du règlement.');
      return;
    }

    this.busy.set(true);
    this.api.addSupplierPayment(invoiceId, this.draft).subscribe({
      next: () => {
        this.reload();
        this.busy.set(false);
        this.changed.emit();
        this.ui.success('Règlement enregistré', 'La facture a été mise à jour.');
      },
      // The interceptor shows the backend's refusal, including an overpayment.
      error: () => this.busy.set(false)
    });
  }

  remove(payment: SupplierPayment): void {
    const invoiceId = this.invoiceId();
    if (!invoiceId || !payment.id) return;
    this.ui.confirmDelete('Supprimer ce règlement ?', () => {
      this.api.deleteSupplierPayment(invoiceId, payment.id!).subscribe({
        next: () => {
          this.reload();
          this.changed.emit();
          this.ui.success('Règlement supprimé', 'La facture a été mise à jour.');
        }
      });
    });
  }

  /** Fills the amount with the whole outstanding balance: the common case. */
  payBalance(): void {
    this.draft = { ...this.draft, amount: Math.max(0, this.remaining()) };
  }

  private resetDraft(): void {
    this.draft = SupplierPaymentsComponent.emptyDraft();
    this.draft.amount = Math.max(0, this.remaining());
  }

  private static emptyDraft(): SupplierPayment {
    return {
      paymentDate: new Date().toISOString().slice(0, 10),
      amount: 0,
      paymentMethod: 'BANK_TRANSFER',
      reference: ''
    };
  }

  methodLabel(value?: string): string {
    return PAYMENT_METHODS.find((method) => method.value === value)?.label ?? (value || '-');
  }
}
