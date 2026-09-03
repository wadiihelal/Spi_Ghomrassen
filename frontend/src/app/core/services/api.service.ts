import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import {
  AmountByLabel,
  Client,
  ClientAdvance,
  AuditLog,
  Apartment,
  ClientPurchase,
  ClientStatement,
  DashboardSummary,
  Expense,
  ExpenseCategory,
  PagedResponse,
  Project,
  ReportScopeParams,
  Supplier,
  SupplierInvoice,
  SupplierTypeOption,
  VatRateOption
} from '../../shared/models/models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiUrl;
  private readonly defaultPageSize = 1000;

  private getPaged<T>(path: string, params?: Record<string, string | number | boolean | null | undefined>): Observable<T[]> {
    let httpParams = new HttpParams()
      .set('page', 0)
      .set('size', this.defaultPageSize);

    Object.entries(params ?? {}).forEach(([key, value]) => {
      if (value !== null && value !== undefined) {
        httpParams = httpParams.set(key, String(value));
      }
    });

    return this.http.get<PagedResponse<T>>(`${this.baseUrl}${path}`, { params: httpParams }).pipe(
      map((response) => response.content ?? [])
    );
  }

  getDashboardSummary(scope?: ReportScopeParams): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(`${this.baseUrl}/dashboard/summary`, {
      params: this.scopeParams(scope)
    });
  }

  getExpenses(): Observable<Expense[]> {
    return this.getPaged<Expense>('/expenses');
  }

  createExpense(payload: Expense): Observable<Expense> {
    return this.http.post<Expense>(`${this.baseUrl}/expenses`, payload);
  }

  updateExpense(id: number, payload: Expense): Observable<Expense> {
    return this.http.put<Expense>(`${this.baseUrl}/expenses/${id}`, payload);
  }

  getExpenseCategories(): Observable<ExpenseCategory[]> {
    return this.getPaged<ExpenseCategory>('/expense-categories');
  }

  createExpenseCategory(payload: ExpenseCategory): Observable<ExpenseCategory> {
    return this.http.post<ExpenseCategory>(`${this.baseUrl}/expense-categories`, payload);
  }

  getProjects(): Observable<Project[]> {
    return this.getPaged<Project>('/projects');
  }

  getProject(id: number): Observable<Project> {
    return this.http.get<Project>(`${this.baseUrl}/projects/${id}`);
  }

  createProject(payload: Project): Observable<Project> {
    return this.http.post<Project>(`${this.baseUrl}/projects`, payload);
  }

  updateProject(id: number, payload: Project): Observable<Project> {
    return this.http.put<Project>(`${this.baseUrl}/projects/${id}`, payload);
  }

  deleteProject(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/projects/${id}`);
  }

  getActiveProjectContext(): Observable<Project> {
    return this.http.get<Project>(`${this.baseUrl}/projects/active-context`);
  }

  setActiveProjectContext(projectId: number): Observable<Project> {
    return this.http.put<Project>(`${this.baseUrl}/projects/active-context/${projectId}`, {});
  }

  clearActiveProjectContext(): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/projects/active-context`);
  }

  getClients(): Observable<Client[]> {
    return this.getPaged<Client>('/clients');
  }

  createClient(payload: Client): Observable<Client> {
    return this.http.post<Client>(`${this.baseUrl}/clients`, payload);
  }

  updateClient(id: number, payload: Client): Observable<Client> {
    return this.http.put<Client>(`${this.baseUrl}/clients/${id}`, payload);
  }

  deleteClient(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/clients/${id}`);
  }

  getSuppliers(): Observable<Supplier[]> {
    return this.getPaged<Supplier>('/suppliers');
  }

  getApartments(): Observable<Apartment[]> {
    return this.getPaged<Apartment>('/apartments');
  }

  createApartment(payload: Apartment): Observable<Apartment> {
    return this.http.post<Apartment>(`${this.baseUrl}/apartments`, payload);
  }

  updateApartment(id: number, payload: Apartment): Observable<Apartment> {
    return this.http.put<Apartment>(`${this.baseUrl}/apartments/${id}`, payload);
  }

  deleteApartment(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/apartments/${id}`);
  }

  getSupplierInvoices(): Observable<SupplierInvoice[]> {
    return this.getPaged<SupplierInvoice>('/supplier-invoices');
  }

  createSupplierInvoice(payload: SupplierInvoice): Observable<SupplierInvoice> {
    return this.http.post<SupplierInvoice>(`${this.baseUrl}/supplier-invoices`, payload);
  }

  updateSupplierInvoice(id: number, payload: SupplierInvoice): Observable<SupplierInvoice> {
    return this.http.put<SupplierInvoice>(`${this.baseUrl}/supplier-invoices/${id}`, payload);
  }

  deleteSupplierInvoice(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/supplier-invoices/${id}`);
  }

  getSupplierTypes(): Observable<SupplierTypeOption[]> {
    return this.getPaged<SupplierTypeOption>('/supplier-types');
  }

  getVatRates(): Observable<VatRateOption[]> {
    return this.getPaged<VatRateOption>('/vat-rates');
  }

  createSupplierType(payload: SupplierTypeOption): Observable<SupplierTypeOption> {
    return this.http.post<SupplierTypeOption>(`${this.baseUrl}/supplier-types`, payload);
  }

  createSupplier(payload: Supplier): Observable<Supplier> {
    return this.http.post<Supplier>(`${this.baseUrl}/suppliers`, payload);
  }

  updateSupplier(id: number, payload: Supplier): Observable<Supplier> {
    return this.http.put<Supplier>(`${this.baseUrl}/suppliers/${id}`, payload);
  }

  deleteSupplier(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/suppliers/${id}`);
  }

  getPurchases(): Observable<ClientPurchase[]> {
    return this.getPaged<ClientPurchase>('/client-purchases');
  }

  createPurchase(payload: ClientPurchase): Observable<ClientPurchase> {
    return this.http.post<ClientPurchase>(`${this.baseUrl}/client-purchases`, payload);
  }

  updatePurchase(id: number, payload: ClientPurchase): Observable<ClientPurchase> {
    return this.http.put<ClientPurchase>(`${this.baseUrl}/client-purchases/${id}`, payload);
  }

  getAdvances(): Observable<ClientAdvance[]> {
    return this.getPaged<ClientAdvance>('/client-advances');
  }

  createAdvance(payload: ClientAdvance): Observable<ClientAdvance> {
    return this.http.post<ClientAdvance>(`${this.baseUrl}/client-advances`, payload);
  }

  updateAdvance(id: number, payload: ClientAdvance): Observable<ClientAdvance> {
    return this.http.put<ClientAdvance>(`${this.baseUrl}/client-advances/${id}`, payload);
  }

  getClientStatements(scope?: ReportScopeParams): Observable<ClientStatement[]> {
    return this.getPaged<ClientStatement>('/reports/clients/statements', this.scopeRecord(scope));
  }

  getAuditLogs(entityType: string, entityId: number): Observable<AuditLog[]> {
    return this.getPaged<AuditLog>(`/audit-logs/by-entity/${entityType}/${entityId}`);
  }

  getExpensesByCategory(scope?: ReportScopeParams): Observable<AmountByLabel[]> {
    return this.getPaged<AmountByLabel>('/reports/expenses/by-category', this.scopeRecord(scope));
  }

  getExpensesByProject(scope?: ReportScopeParams): Observable<AmountByLabel[]> {
    return this.getPaged<AmountByLabel>('/reports/expenses/by-project', this.scopeRecord(scope));
  }

  downloadReportsExcel(scope: ReportScopeParams): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/reports/export/excel`, {
      params: this.scopeParams(scope),
      responseType: 'blob'
    });
  }

  downloadReportsPdf(scope: ReportScopeParams): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/reports/export/pdf`, {
      params: this.scopeParams(scope),
      responseType: 'blob'
    });
  }

  private scopeRecord(scope?: ReportScopeParams): Record<string, string | number | null | undefined> {
    return {
      projectId: scope?.projectId ?? undefined,
      year: scope?.year ?? undefined,
      month: scope?.month ?? undefined
    };
  }

  private scopeParams(scope?: ReportScopeParams): HttpParams {
    let params = new HttpParams();
    Object.entries(this.scopeRecord(scope)).forEach(([key, value]) => {
      if (value !== null && value !== undefined) {
        params = params.set(key, String(value));
      }
    });
    return params;
  }
}
