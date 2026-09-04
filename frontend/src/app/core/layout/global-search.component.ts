import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, HostListener, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { toObservable, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { debounceTime, distinctUntilChanged, of, switchMap, catchError } from 'rxjs';
import { ApiService } from '../services/api.service';
import { ProjectContextService } from '../services/project-context.service';
import { SearchHit } from '../../shared/models/models';

/** Where each kind of hit lives, and what to call it. */
const TYPE_META: Record<SearchHit['type'], { label: string; route: string; icon: string }> = {
  CLIENT: { label: 'Clients', route: '/clients', icon: 'pi pi-user' },
  APARTMENT: { label: 'Appartements', route: '/apartments', icon: 'pi pi-building' },
  PURCHASE: { label: 'Contrats de vente', route: '/purchases', icon: 'pi pi-file-edit' },
  SUPPLIER: { label: 'Fournisseurs', route: '/suppliers', icon: 'pi pi-id-card' },
  SUPPLIER_INVOICE: { label: 'Factures fournisseurs', route: '/supplier-invoices', icon: 'pi pi-receipt' }
};

/** Keystrokes settle for this long before the server is asked. */
const DEBOUNCE_MS = 250;

/**
 * The search box in the top bar (UX-08).
 *
 * <p>Type a name, a lot number or a reference from any screen; picking a hit opens the matching
 * list already narrowed to it. Results come grouped by type so a name that is both a client and
 * a supplier reads unambiguously.</p>
 */
@Component({
  selector: 'app-global-search',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './global-search.component.html',
  styleUrl: './global-search.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class GlobalSearchComponent {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly projectContext = inject(ProjectContextService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly host = inject(ElementRef<HTMLElement>);

  readonly query = signal('');
  readonly hits = signal<SearchHit[]>([]);
  readonly open = signal(false);
  readonly searching = signal(false);
  /** Index of the highlighted hit, for the keyboard. */
  readonly active = signal(0);

  /** Hits grouped by type in a fixed order, so the panel reads the same every time. */
  readonly groups = computed(() => {
    const byType = new Map<SearchHit['type'], SearchHit[]>();
    for (const hit of this.hits()) {
      byType.set(hit.type, [...(byType.get(hit.type) ?? []), hit]);
    }
    return [...byType.entries()].map(([type, items]) => ({ type, meta: TYPE_META[type], items }));
  });

  constructor() {
    toObservable(this.query).pipe(
      debounceTime(DEBOUNCE_MS),
      distinctUntilChanged(),
      switchMap((text) => {
        const trimmed = text.trim();
        if (trimmed.length < 2) {
          this.searching.set(false);
          return of([] as SearchHit[]);
        }
        this.searching.set(true);
        return this.api.search(trimmed, this.projectContext.selectedProjectId())
          .pipe(catchError(() => of([] as SearchHit[])));
      }),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe((hits) => {
      this.hits.set(hits);
      this.active.set(0);
      this.searching.set(false);
      this.open.set(this.query().trim().length >= 2);
    });
  }

  onInput(value: string): void {
    this.query.set(value);
  }

  onFocus(): void {
    if (this.hits().length) this.open.set(true);
  }

  onKeydown(event: KeyboardEvent): void {
    const flat = this.hits();
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.active.set(Math.min(this.active() + 1, flat.length - 1));
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.active.set(Math.max(this.active() - 1, 0));
        break;
      case 'Enter':
        if (flat[this.active()]) this.select(flat[this.active()]);
        break;
      case 'Escape':
        this.close();
        break;
    }
  }

  /** The hit's list, narrowed to what was typed so the row is the first thing on screen. */
  select(hit: SearchHit): void {
    this.router.navigate([TYPE_META[hit.type].route], { queryParams: { search: hit.label } });
    this.close();
    this.query.set('');
  }

  flatIndex(hit: SearchHit): number {
    return this.hits().indexOf(hit);
  }

  close(): void {
    this.open.set(false);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.host.nativeElement.contains(event.target as Node)) {
      this.close();
    }
  }
}
