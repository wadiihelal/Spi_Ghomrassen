import { Pipe, PipeTransform, inject } from '@angular/core';
import { DecimalPipe } from '@angular/common';

/**
 * Formats an amount as Tunisian dinars, always at three decimals.
 *
 * <p>The dinar divides into 1000 millimes and the backend stores every amount at
 * `scale = 3`, so truncating the display to two decimals loses real money: an invoice of
 * 22 015,750 DT would read "22 015,75". Rendered under the `fr-TN` locale, this gives
 * "22 015,750 DT".</p>
 *
 * <p>`| dinar` for a full amount, `| dinar:'short'` for KPI tiles where the millimes are
 * noise, and `| dinar:'bare'` when the unit is already in the surrounding text.</p>
 */
@Pipe({ name: 'dinar', standalone: true })
export class DinarPipe implements PipeTransform {
  private readonly decimal = inject(DecimalPipe);

  transform(value: number | null | undefined, style: 'full' | 'short' | 'bare' = 'full'): string {
    if (value === null || value === undefined || Number.isNaN(value)) {
      return style === 'bare' ? '0,000' : '0,000 DT';
    }
    const digits = style === 'short' ? '1.0-0' : '1.3-3';
    const formatted = this.decimal.transform(value, digits) ?? '0';
    return style === 'bare' ? formatted : `${formatted} DT`;
  }
}
