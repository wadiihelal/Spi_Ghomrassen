import { Injectable, inject, signal } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { Observable, of } from 'rxjs';
import { catchError, filter, map, tap } from 'rxjs/operators';
import { ApiService } from './api.service';
import { Project } from '../../shared/models/models';

/**
 * Which project the console is scoped to.
 *
 * <p>A signal rather than a BehaviorSubject (FE-03): components read it inside a
 * {@link effect} or a {@link computed} and need no subscription to release.</p>
 */
@Injectable({ providedIn: 'root' })
export class ProjectContextService {
  private readonly api = inject(ApiService);
  private readonly selected = signal<number | null>(null);

  /**
   * The resolved scope, boxed so that re-selecting the same project still emits. Null until
   * the stored context has come back from the server.
   */
  private readonly scope = signal<{ projectId: number | null } | null>(null);

  /** The selected project, readable but not writable from outside this service. */
  readonly selectedProjectId = this.selected.asReadonly();

  /**
   * The project every screen should scope its data to.
   *
   * <p>It emits only once the stored context is known, and again on every change. Screens must
   * not load on the initial unresolved state: an unscoped request fired then can answer after
   * the scoped one and overwrite it, showing every project's figures under one project's
   * name.</p>
   */
  readonly scope$: Observable<number | null> = toObservable(this.scope).pipe(
    filter((scope): scope is { projectId: number | null } => scope !== null),
    map((scope) => scope.projectId)
  );

  loadSelectedProjectId(): Observable<number | null> {
    return this.api.getActiveProjectContext().pipe(
      map((project) => project?.id ?? null),
      tap((projectId) => this.publish(projectId)),
      catchError(() => {
        this.publish(null);
        return of(null);
      })
    );
  }

  setSelectedProjectId(projectId: number | null): Observable<number | null> {
    const request$ = projectId == null
      ? this.api.clearActiveProjectContext().pipe(map(() => null))
      : this.api.setActiveProjectContext(projectId).pipe(map((project) => project.id ?? null));

    return request$.pipe(tap((selectedId) => this.publish(selectedId)));
  }

  /** Records the scope and announces it, in that order, so readers see a settled state. */
  private publish(projectId: number | null): void {
    this.selected.set(projectId);
    this.scope.set({ projectId });
  }

  getSelectedProject(projects: Project[]): Project | null {
    const selectedId = this.selected();
    if (!selectedId) return null;
    return projects.find((project) => project.id === selectedId) ?? null;
  }
}
