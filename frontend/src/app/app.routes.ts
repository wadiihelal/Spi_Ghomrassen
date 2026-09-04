import { Routes } from '@angular/router';

/**
 * Every feature is loaded on demand (FE-04). Statically importing all eleven components, and
 * with them their PrimeNG modules, put the whole console in the initial bundle.
 */
export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  {
    path: 'dashboard',
    loadComponent: () => import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent)
  },
  {
    path: 'expenses',
    loadComponent: () => import('./features/expenses/expenses.component').then((m) => m.ExpensesComponent)
  },
  {
    path: 'clients',
    loadComponent: () => import('./features/clients/clients.component').then((m) => m.ClientsComponent)
  },
  {
    path: 'suppliers',
    loadComponent: () => import('./features/suppliers/suppliers.component').then((m) => m.SuppliersComponent)
  },
  {
    path: 'projects',
    loadComponent: () => import('./features/projects/projects.component').then((m) => m.ProjectsComponent)
  },
  {
    path: 'projects/:id',
    loadComponent: () =>
      import('./features/projects/project-detail.component').then((m) => m.ProjectDetailComponent)
  },
  {
    path: 'apartments',
    loadComponent: () => import('./features/apartments/apartments.component').then((m) => m.ApartmentsComponent)
  },
  {
    path: 'purchases',
    loadComponent: () => import('./features/purchases/purchases.component').then((m) => m.PurchasesComponent)
  },
  {
    path: 'schedules',
    loadComponent: () => import('./features/schedules/schedules.component').then((m) => m.SchedulesComponent)
  },
  {
    path: 'advances',
    loadComponent: () => import('./features/advances/advances.component').then((m) => m.AdvancesComponent)
  },
  {
    path: 'supplier-invoices',
    loadComponent: () =>
      import('./features/supplier-invoices/supplier-invoices.component').then((m) => m.SupplierInvoicesComponent)
  },
  {
    path: 'reports',
    loadComponent: () => import('./features/reports/reports.component').then((m) => m.ReportsComponent)
  },
  { path: '**', redirectTo: 'dashboard' }
];
