import { Routes } from '@angular/router';
import { DashboardComponent } from './features/dashboard/dashboard.component';
import { ExpensesComponent } from './features/expenses/expenses.component';
import { PurchasesComponent } from './features/purchases/purchases.component';
import { AdvancesComponent } from './features/advances/advances.component';
import { ReportsComponent } from './features/reports/reports.component';
import { ClientsComponent } from './features/clients/clients.component';
import { SuppliersComponent } from './features/suppliers/suppliers.component';
import { ProjectsComponent } from './features/projects/projects.component';
import { ProjectDetailComponent } from './features/projects/project-detail.component';
import { ApartmentsComponent } from './features/apartments/apartments.component';
import { SupplierInvoicesComponent } from './features/supplier-invoices/supplier-invoices.component';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  { path: 'dashboard', component: DashboardComponent },
  { path: 'expenses', component: ExpensesComponent },
  { path: 'clients', component: ClientsComponent },
  { path: 'suppliers', component: SuppliersComponent },
  { path: 'projects', component: ProjectsComponent },
  { path: 'projects/:id', component: ProjectDetailComponent },
  { path: 'apartments', component: ApartmentsComponent },
  { path: 'purchases', component: PurchasesComponent },
  { path: 'advances', component: AdvancesComponent },
  { path: 'supplier-invoices', component: SupplierInvoicesComponent },
  { path: 'reports', component: ReportsComponent },
  { path: '**', redirectTo: 'dashboard' }
];
