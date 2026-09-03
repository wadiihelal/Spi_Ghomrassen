import { DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subject, debounceTime } from 'rxjs';
import { PageQuery, PagedResponse } from '../../shared/models/models';

/** Rows per page, matching the first option offered by the pagers. */
export const DEFAULT_PAGE_SIZE = 25;

/** How long to wait after the last keystroke before asking the server again. */
const FILTER_DEBOUNCE_MS = 300;

/** Minimal shape of PrimeNG's lazy-load event that this helper needs. */
export interface LazyPageEvent {
  first?: number | null;
  rows?: number | null;
  sortField?: string | string[] | null;
  sortOrder?: number | null;
}

/**
 * Page state for a PrimeNG table in lazy mode (PERF-02).
 *
 * <p>Holds one page of rows and the true total, so the pager is honest; before this, every
 * screen requested page 0 of 1000 rows and filtered in TypeScript, silently losing anything
 * past the thousandth row because nothing read {@code totalElements}.</p>
 */
export class LazyTable<T> {

  rows: T[] = [];
  totalRecords = 0;
  loading = false;
  readonly pageSize = DEFAULT_PAGE_SIZE;

  private lastEvent: LazyPageEvent = { first: 0, rows: DEFAULT_PAGE_SIZE };
  private readonly filterChanged = new Subject<void>();

  /**
   * @param fetch      asks the server for one page; called with the resolved page query and
   *                   expected to read the component's current filters itself
   * @param destroyRef ties the debounce subscription to the component's lifetime, so navigating
   *                   away releases it (FE-03)
   */
  constructor(
    private readonly fetch: (query: PageQuery) => Observable<PagedResponse<T>>,
    destroyRef: DestroyRef
  ) {
    this.filterChanged
      .pipe(debounceTime(FILTER_DEBOUNCE_MS), takeUntilDestroyed(destroyRef))
      .subscribe(() => this.load({ ...this.lastEvent, first: 0 }));
  }

  /** Bound to the table's (onLazyLoad): paging and sorting both arrive here. */
  onLazyLoad(event: LazyPageEvent): void {
    this.load(event);
  }

  /** Bound to every filter control: debounced, and always returns to the first page. */
  onFilterChange(): void {
    this.filterChanged.next();
  }

  /** Re-requests the current page, e.g. after a save or a delete. */
  reload(): void {
    this.load(this.lastEvent);
  }

  private load(event: LazyPageEvent): void {
    this.lastEvent = event;
    const size = event.rows ?? this.pageSize;
    const query: PageQuery = {
      page: Math.floor((event.first ?? 0) / size),
      size,
      sort: LazyTable.sortOf(event)
    };

    this.loading = true;
    this.fetch(query).subscribe({
      next: (response) => {
        this.rows = response.content ?? [];
        this.totalRecords = response.totalElements ?? this.rows.length;
        this.loading = false;
      },
      // The HTTP error interceptor already surfaces the reason (FE-02).
      error: () => (this.loading = false)
    });
  }

  private static sortOf(event: LazyPageEvent): string | undefined {
    const field = Array.isArray(event.sortField) ? event.sortField[0] : event.sortField;
    if (!field) {
      return undefined;
    }
    return `${field},${event.sortOrder === -1 ? 'desc' : 'asc'}`;
  }
}
