import { Pipe, PipeTransform, inject } from '@angular/core';
import { DecimalPipe } from '@angular/common';

/**
 * Formats a share as a percentage with at most one decimal, e.g. "18,5 %".
 *
 * <p>Takes a value already expressed in percent (18.5), not a fraction. The space before the
 * sign is the French convention.</p>
 */
@Pipe({ name: 'percentShare', standalone: true })
export class PercentSharePipe implements PipeTransform {
  private readonly decimal = inject(DecimalPipe);

  transform(value: number | null | undefined): string {
    const formatted = this.decimal.transform(value ?? 0, '1.0-1') ?? '0';
    return `${formatted} %`;
  }
}
