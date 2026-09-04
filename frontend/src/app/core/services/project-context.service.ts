import { Injectable, inject, signal } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
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

  /** The selected project, readable but not writable from outside this service. */
  readonly selectedProjectId = this.selected.asReadonly();

  loadSelectedProjectId(): Observable<number | null> {
    return this.api.getActiveProjectContext().pipe(
      map((project) => project?.id ?? null),
      tap((projectId) => this.selected.set(projectId)),
      catchError(() => {
        this.selected.set(null);
        return of(null);
      })
    );
  }

  setSelectedProjectId(projectId: number | null): Observable<number | null> {
    const request$ = projectId == null
      ? this.api.clearActiveProjectContext().pipe(map(() => null))
      : this.api.setActiveProjectContext(projectId).pipe(map((project) => project.id ?? null));

    return request$.pipe(tap((selectedId) => this.selected.set(selectedId)));
  }

  getSelectedProject(projects: Project[]): Project | null {
    const selectedId = this.selected();
    if (!selectedId) return null;
    return projects.find((project) => project.id === selectedId) ?? null;
  }
}
