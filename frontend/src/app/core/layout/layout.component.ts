import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { DropdownModule } from 'primeng/dropdown';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../services/api.service';
import { ProjectContextService } from '../services/project-context.service';
import { Project } from '../../shared/models/models';
import { forkJoin } from 'rxjs';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, RouterLinkActive, ButtonModule, ToastModule, ConfirmDialogModule, DropdownModule],
  templateUrl: './layout.component.html',
  styleUrl: './layout.component.css'
})
export class LayoutComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly projectContext = inject(ProjectContextService);

  sidebarOpen = false;
  currentDateTime = new Date();
  projects: Project[] = [];
  selectedProjectId: number | null = null;
  private timerId: ReturnType<typeof setInterval> | null = null;

  dailyNavItems = [
    { label: 'Accueil', route: '/dashboard', icon: 'pi pi-home' },
    { label: 'Stock appartements', route: '/apartments', icon: 'pi pi-building' },
    { label: 'Factures fournisseurs', route: '/supplier-invoices', icon: 'pi pi-receipt' },
    { label: 'Dépenses', route: '/expenses', icon: 'pi pi-wallet' },
    { label: 'Ventes clients', route: '/purchases', icon: 'pi pi-shopping-cart' },
    { label: 'Paiements clients', route: '/advances', icon: 'pi pi-credit-card' },
    { label: 'Rapports', route: '/reports', icon: 'pi pi-file' }
  ];

  settingsNavItems = [
    { label: 'Projets', route: '/projects', icon: 'pi pi-briefcase' },
    { label: 'Clients', route: '/clients', icon: 'pi pi-users' },
    { label: 'Fournisseurs', route: '/suppliers', icon: 'pi pi-id-card' }
  ];

  get selectedProject(): Project | null {
    return this.projectContext.getSelectedProject(this.projects);
  }

  ngOnInit(): void {
    this.timerId = setInterval(() => {
      this.currentDateTime = new Date();
    }, 1000);

    forkJoin({
      projects: this.api.getProjects(),
      selectedProjectId: this.projectContext.loadSelectedProjectId()
    }).subscribe({
      next: ({ projects, selectedProjectId }) => {
        this.projects = projects;
        this.selectedProjectId = selectedProjectId;
      },
      error: () => {
        this.api.getProjects().subscribe({ next: (data) => (this.projects = data) });
      }
    });

    this.projectContext.selectedProjectId$.subscribe((projectId) => {
      this.selectedProjectId = projectId;
    });
  }

  ngOnDestroy(): void {
    if (this.timerId) {
      clearInterval(this.timerId);
    }
  }

  toggleSidebar(): void {
    this.sidebarOpen = !this.sidebarOpen;
  }

  selectProject(projectId: number | null): void {
    this.projectContext.setSelectedProjectId(projectId).subscribe({
      next: () => {
        this.sidebarOpen = false;
      }
    });
  }
}
