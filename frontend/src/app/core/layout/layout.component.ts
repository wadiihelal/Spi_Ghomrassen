import { ChangeDetectionStrategy, Component, DestroyRef, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { GlobalSearchComponent } from './global-search.component';
import { ButtonModule } from 'primeng/button';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { DropdownModule } from 'primeng/dropdown';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../services/api.service';
import { ProjectContextService } from '../services/project-context.service';
import { Project } from '../../shared/models/models';
import { forkJoin } from 'rxjs';

/** The header shows the date and the hour, so a minute's resolution is enough. */
const CLOCK_INTERVAL_MS = 60_000;

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, RouterLinkActive, ButtonModule, ToastModule, ConfirmDialogModule, DropdownModule, GlobalSearchComponent],
  templateUrl: './layout.component.html',
  styleUrl: './layout.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LayoutComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly projectContext = inject(ProjectContextService);
  private readonly destroyRef = inject(DestroyRef);

  readonly sidebarOpen = signal(false);
  /**
   * Header date. Refreshed every minute, not every second (FE-06): the old one-second timer
   * forced an application-wide change-detection pass sixty times a minute for a display that
   * shows no seconds.
   */
  readonly currentDateTime = signal(new Date());
  readonly projects = signal<Project[]>([]);
  readonly selectedProjectId = signal<number | null>(null);
  private timerId: ReturnType<typeof setInterval> | null = null;

  dailyNavItems = [
    { label: 'Accueil', route: '/dashboard', icon: 'pi pi-home' },
    { label: 'Plan de vente', route: '/sales-board', icon: 'pi pi-th-large' },
    { label: 'Stock appartements', route: '/apartments', icon: 'pi pi-building' },
    { label: 'Factures fournisseurs', route: '/supplier-invoices', icon: 'pi pi-receipt' },
    { label: 'Dépenses', route: '/expenses', icon: 'pi pi-wallet' },
    { label: 'Ventes clients', route: '/purchases', icon: 'pi pi-shopping-cart' },
    { label: 'Paiements clients', route: '/advances', icon: 'pi pi-credit-card' },
    { label: 'Échéancier', route: '/schedules', icon: 'pi pi-calendar-clock' },
    { label: 'Rapports', route: '/reports', icon: 'pi pi-file' }
  ];

  settingsNavItems = [
    { label: 'Projets', route: '/projects', icon: 'pi pi-briefcase' },
    { label: 'Clients', route: '/clients', icon: 'pi pi-users' },
    { label: 'Fournisseurs', route: '/suppliers', icon: 'pi pi-id-card' },
    { label: 'Journal', route: '/audit', icon: 'pi pi-history' }
  ];

  get selectedProject(): Project | null {
    return this.projectContext.getSelectedProject(this.projects());
  }

  ngOnInit(): void {
    this.timerId = setInterval(() => this.currentDateTime.set(new Date()), CLOCK_INTERVAL_MS);

    forkJoin({
      projects: this.api.getProjects(),
      selectedProjectId: this.projectContext.loadSelectedProjectId()
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ({ projects, selectedProjectId }) => {
        this.projects.set(projects);
        this.selectedProjectId.set(selectedProjectId);
      },
      error: () => {
        this.api.getProjects().pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe({ next: (data) => this.projects.set(data) });
      }
    });

    this.projectContext.scope$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((projectId) => {
      this.selectedProjectId.set(projectId);
    });
  }

  ngOnDestroy(): void {
    if (this.timerId) {
      clearInterval(this.timerId);
    }
  }

  toggleSidebar(): void {
    this.sidebarOpen.update((open) => !open);
  }

  selectProject(projectId: number | null): void {
    this.projectContext.setSelectedProjectId(projectId).subscribe({
      next: () => {
        this.sidebarOpen.set(false);
      }
    });
  }
}
