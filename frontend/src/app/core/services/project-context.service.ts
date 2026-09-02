import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { ApiService } from './api.service';
import { Project } from '../../shared/models/models';

@Injectable({ providedIn: 'root' })
export class ProjectContextService {
  private readonly api = inject(ApiService);
  private readonly selectedProjectIdSubject = new BehaviorSubject<number | null>(null);
  readonly selectedProjectId$ = this.selectedProjectIdSubject.asObservable();

  get selectedProjectId(): number | null {
    return this.selectedProjectIdSubject.value;
  }

  loadSelectedProjectId(): Observable<number | null> {
    return this.api.getActiveProjectContext().pipe(
      map((project) => project?.id ?? null),
      tap((projectId) => this.selectedProjectIdSubject.next(projectId)),
      catchError(() => {
        this.selectedProjectIdSubject.next(null);
        return of(null);
      })
    );
  }

  setSelectedProjectId(projectId: number | null): Observable<number | null> {
    const request$ = projectId == null
      ? this.api.clearActiveProjectContext().pipe(map(() => null))
      : this.api.setActiveProjectContext(projectId).pipe(map((project) => project.id ?? null));

    return request$.pipe(
      tap((selectedId) => this.selectedProjectIdSubject.next(selectedId))
    );
  }

  getSelectedProject(projects: Project[]): Project | null {
    const selectedId = this.selectedProjectId;
    if (!selectedId) return null;
    return projects.find((project) => project.id === selectedId) ?? null;
  }
}
