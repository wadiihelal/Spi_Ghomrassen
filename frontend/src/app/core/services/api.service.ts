import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

/**
 * Reference lists (projects, clients, categories, VAT rates) are small and bounded, so one
 * request is fine. Business tables must never rely on this.
 */
const REFERENCE_LIST_SIZE = 500;
import {
  AmountByLabel,
  Attachment,
  AttachmentOwnerType,
  Client,
  ClientAdvance,
  InstallmentLine,
  InstallmentStatus,
  InstallmentSummary,
  PaymentSchedule,
  PaymentInstallment,
  ScheduleTemplate,
  AuditLog,
  Apartment,
  ClientPurchase,
  ClientStatement,
  DashboardSummary,
  Expense,
  ExpenseCategory,
  ListFilter,
  PageQuery,
  PagedResponse,
  PayablesSummary,
  SalesBoard,
  SalesStatus,
  Project,
  ReportScopeParams,
  Supplier,
  SupplierInvoice,
  SupplierPayment,
  SupplierTypeOption,
  VatRateOption
} from '../../shared/models/models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiUrl;

  /**
   * A reference list small enough to hold in a dropdown: projects, clients, categories,
   * supplier types, VAT rates. Business tables never use this — they page server-side
   * through {@link page} (PERF-02).
   */
  private getReferenceList<T>(
    path: string,
    extra?: Record<string, string | number | null | undefined>
  ): Observable<T[]> {
    let params = new HttpParams().set('page', 0).set('size', REFERENCE_LIST_SIZE);
    Object.entries(extra ?? {}).forEach(([key, value]) => {
      if (value !== null && value !== undefined) {
        params = params.set(key, String(value));
      }
    });
    return this.http.get<PagedResponse<T>>(`${this.baseUrl}${path}`, { params }).pipe(
      map((response) => response.content ?? [])
    );
  }

  /** One page of a filtered list, with the true totals the caller needs for the pager. */
  private page<T>(path: string, filter: ListFilter, query: PageQuery): Observable<PagedResponse<T>> {
    let params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size);
    if (query.sort) {
      params = params.set('sort', query.sort);
    }
    Object.entries(filter).forEach(([key, value]) => {
      if (value !== null && value !== undefined && value !== '') {
        params = params.set(key, String(value));
      }
    });
    return this.http.get<PagedResponse<T>>(`${this.baseUrl}${path}`, { params });
  }

  getDashboardSummary(scope?: ReportScopeParams): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(`${this.baseUrl}/dashboard/summary`, {
      params: this.scopeParams(scope)
    });
  }

  getExpenses(filter: ListFilter, query: PageQuery): Observable<PagedResponse<Expense>> {
    return this.page<Expense>('/expenses', filter, query);
  }

  createExpense(payload: Expense): Observable<Expense> {
    return this.http.post<Expense>(`${this.baseUrl}/expenses`, payload);
  }

  updateExpense(id: number, payload: Expense): Observable<Expense> {
    return this.http.put<Expense>(`${this.baseUrl}/expenses/${id}`, payload);
  }

  deleteExpense(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/expenses/${id}`);
  }

  getExpenseCategories(): Observable<ExpenseCategory[]> {
    return this.getReferenceList<ExpenseCategory>('/expense-categories');
  }

  createExpenseCategory(payload: ExpenseCategory): Observable<ExpenseCategory> {
    return this.http.post<ExpenseCategory>(`${this.baseUrl}/expense-categories`, payload);
  }

  getProjects(): Observable<Project[]> {
    return this.getReferenceList<Project>('/projects');
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

  /**
   * Clients as a dropdown and list source. Optionally narrowed to one project, so the clients
   * screen no longer filters a full list in the browser.
   */
  getClients(projectId?: number | null): Observable<Client[]> {
    return this.getReferenceList<Client>('/clients', { projectId });
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
    return this.getReferenceList<Supplier>('/suppliers');
  }

  getApartments(filter: ListFilter, query: PageQuery): Observable<PagedResponse<Apartment>> {
    return this.page<Apartment>('/apartments', filter, query);
  }

  /** Apartments as a dropdown source; only ever used for a single project. */
  getApartmentOptions(projectId?: number | null): Observable<Apartment[]> {
    return this.page<Apartment>('/apartments', { projectId }, { page: 0, size: REFERENCE_LIST_SIZE })
      .pipe(map((response) => response.content ?? []));
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

  getSupplierInvoices(filter: ListFilter, query: PageQuery): Observable<PagedResponse<SupplierInvoice>> {
    return this.page<SupplierInvoice>('/supplier-invoices', filter, query);
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
    return this.getReferenceList<SupplierTypeOption>('/supplier-types');
  }

  getVatRates(): Observable<VatRateOption[]> {
    return this.getReferenceList<VatRateOption>('/vat-rates');
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

  getPurchases(filter: ListFilter, query: PageQuery): Observable<PagedResponse<ClientPurchase>> {
    return this.page<ClientPurchase>('/client-purchases', filter, query);
  }

  /** Contracts for one project, used to look up an apartment's contract in a form. */
  getPurchaseOptions(projectId?: number | null): Observable<ClientPurchase[]> {
    return this.page<ClientPurchase>('/client-purchases', { projectId }, { page: 0, size: REFERENCE_LIST_SIZE })
      .pipe(map((response) => response.content ?? []));
  }

  createPurchase(payload: ClientPurchase): Observable<ClientPurchase> {
    return this.http.post<ClientPurchase>(`${this.baseUrl}/client-purchases`, payload);
  }

  updatePurchase(id: number, payload: ClientPurchase): Observable<ClientPurchase> {
    return this.http.put<ClientPurchase>(`${this.baseUrl}/client-purchases/${id}`, payload);
  }

  deletePurchase(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/client-purchases/${id}`);
  }

  getAdvances(filter: ListFilter, query: PageQuery): Observable<PagedResponse<ClientAdvance>> {
    return this.page<ClientAdvance>('/client-advances', filter, query);
  }

  /** Advances for one apartment, used by the advance form's ceiling hint. */
  getAdvancesForApartment(apartmentId: number): Observable<ClientAdvance[]> {
    return this.page<ClientAdvance>('/client-advances', { apartmentId }, { page: 0, size: REFERENCE_LIST_SIZE })
      .pipe(map((response) => response.content ?? []));
  }

  createAdvance(payload: ClientAdvance): Observable<ClientAdvance> {
    return this.http.post<ClientAdvance>(`${this.baseUrl}/client-advances`, payload);
  }

  updateAdvance(id: number, payload: ClientAdvance): Observable<ClientAdvance> {
    return this.http.put<ClientAdvance>(`${this.baseUrl}/client-advances/${id}`, payload);
  }

  deleteAdvance(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/client-advances/${id}`);
  }

  // --- Printed documents (UX-06) --------------------------------------------

  /**
   * Address of a printable document. The document is a plain GET, so the browser's own PDF
   * viewer opens it and offers to print or save: no blob juggling in the client.
   */
  receiptUrl(advanceId: number): string {
    return `${this.baseUrl}/documents/advances/${advanceId}/receipt`;
  }

  clientStatementUrl(clientId: number): string {
    return `${this.baseUrl}/documents/clients/${clientId}/statement`;
  }

  vatSummaryUrl(year: number, month: number, projectId?: number | null): string {
    const scope = projectId === null || projectId === undefined ? '' : `?projectId=${projectId}`;
    return `${this.baseUrl}/documents/vat/${year}/${String(month).padStart(2, '0')}${scope}`;
  }

  // --- Sales board (UX-05) --------------------------------------------------

  /** The commercial state of the stock, laid out by block and floor. */
  getSalesBoard(projectId?: number | null): Observable<SalesBoard> {
    let params = new HttpParams();
    if (projectId !== null && projectId !== undefined) {
      params = params.set('projectId', projectId);
    }
    return this.http.get<SalesBoard>(`${this.baseUrl}/apartments/sales-board`, { params });
  }

  /** Holds, sells or delivers one unit; the backend refuses a state the contract contradicts. */
  changeSalesStatus(apartmentId: number, status: SalesStatus): Observable<Apartment> {
    return this.http.patch<Apartment>(`${this.baseUrl}/apartments/${apartmentId}/sales-status`, null, {
      params: new HttpParams().set('status', status)
    });
  }

  // --- Supplier settlement (UX-04) ------------------------------------------

  /** What is still owed to suppliers, and how much of it is late. */
  getPayablesSummary(projectId?: number | null): Observable<PayablesSummary> {
    let params = new HttpParams();
    if (projectId !== null && projectId !== undefined) {
      params = params.set('projectId', projectId);
    }
    return this.http.get<PayablesSummary>(`${this.baseUrl}/supplier-invoices/payables-summary`, { params });
  }

  getSupplierPayments(invoiceId: number): Observable<SupplierPayment[]> {
    return this.http.get<SupplierPayment[]>(`${this.baseUrl}/supplier-invoices/${invoiceId}/payments`);
  }

  addSupplierPayment(invoiceId: number, payment: SupplierPayment): Observable<SupplierPayment> {
    return this.http.post<SupplierPayment>(`${this.baseUrl}/supplier-invoices/${invoiceId}/payments`, payment);
  }

  deleteSupplierPayment(invoiceId: number, paymentId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/supplier-invoices/${invoiceId}/payments/${paymentId}`);
  }

  // --- Payment schedules (UX-03) --------------------------------------------

  /** The schedule of one contract, with each line's settlement resolved by the backend. */
  getSchedule(purchaseId: number): Observable<PaymentSchedule> {
    return this.http.get<PaymentSchedule>(`${this.baseUrl}/client-purchases/${purchaseId}/schedule`);
  }

  /** Replaces the whole schedule; the backend refuses a plan that misses the contract total. */
  saveSchedule(purchaseId: number, lines: InstallmentLine[]): Observable<PaymentSchedule> {
    return this.http.put<PaymentSchedule>(
      `${this.baseUrl}/client-purchases/${purchaseId}/schedule`, { lines });
  }

  generateSchedule(purchaseId: number, template: ScheduleTemplate): Observable<PaymentSchedule> {
    return this.http.post<PaymentSchedule>(
      `${this.baseUrl}/client-purchases/${purchaseId}/schedule/generate`, template);
  }

  clearSchedule(purchaseId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/client-purchases/${purchaseId}/schedule`);
  }

  /** Instalments across every contract, for the échéancier screen. */
  getInstallments(filter: {
    projectId?: number | null;
    clientId?: number | null;
    status?: InstallmentStatus | null;
    dueFrom?: string | null;
    dueTo?: string | null;
  }): Observable<PaymentInstallment[]> {
    let params = new HttpParams();
    Object.entries(filter).forEach(([key, value]) => {
      if (value !== null && value !== undefined && value !== '') {
        params = params.set(key, String(value));
      }
    });
    return this.http.get<PaymentInstallment[]>(`${this.baseUrl}/installments`, { params });
  }

  getInstallmentSummary(projectId?: number | null): Observable<InstallmentSummary> {
    let params = new HttpParams();
    if (projectId !== null && projectId !== undefined) {
      params = params.set('projectId', projectId);
    }
    return this.http.get<InstallmentSummary>(`${this.baseUrl}/installments/summary`, { params });
  }

  /** Proof files attached to one document, most recent first (FE-05). */
  getAttachments(ownerType: AttachmentOwnerType, ownerId: number): Observable<Attachment[]> {
    const params = new HttpParams().set('ownerType', ownerType).set('ownerId', ownerId);
    return this.http.get<Attachment[]>(`${this.baseUrl}/attachments`, { params });
  }

  uploadAttachment(ownerType: AttachmentOwnerType, ownerId: number, file: File): Observable<Attachment> {
    const body = new FormData();
    body.append('file', file, file.name);
    const params = new HttpParams().set('ownerType', ownerType).set('ownerId', ownerId);
    return this.http.post<Attachment>(`${this.baseUrl}/attachments`, body, { params });
  }

  deleteAttachment(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/attachments/${id}`);
  }

  /** URL the browser can open to view or save the file. */
  attachmentUrl(id: number): string {
    return `${this.baseUrl}/attachments/${id}`;
  }

  getClientStatements(scope?: ReportScopeParams): Observable<ClientStatement[]> {
    return this.getReferenceList<ClientStatement>('/reports/clients/statements', this.scopeRecord(scope));
  }

  getAuditLogs(entityType: string, entityId: number): Observable<AuditLog[]> {
    return this.getReferenceList<AuditLog>(`/audit-logs/by-entity/${entityType}/${entityId}`);
  }

  getExpensesByCategory(scope?: ReportScopeParams): Observable<AmountByLabel[]> {
    return this.getReferenceList<AmountByLabel>('/reports/expenses/by-category', this.scopeRecord(scope));
  }

  getExpensesByProject(scope?: ReportScopeParams): Observable<AmountByLabel[]> {
    return this.getReferenceList<AmountByLabel>('/reports/expenses/by-project', this.scopeRecord(scope));
  }

  /** Expense totals per calendar month, for the dashboard's trend. */
  getExpensesByMonth(scope?: ReportScopeParams): Observable<AmountByLabel[]> {
    return this.getReferenceList<AmountByLabel>('/reports/expenses/by-month', this.scopeRecord(scope));
  }

  /** Advance totals grouped by payment method, for the advances screen's KPI strip. */
  getAdvancesByPaymentMethod(scope?: ReportScopeParams): Observable<AmountByLabel[]> {
    return this.getReferenceList<AmountByLabel>('/reports/advances/by-payment-method', this.scopeRecord(scope));
  }

  /** Contracted totals grouped by project, for the dashboard's margin per project. */
  getPurchasesByProject(scope?: ReportScopeParams): Observable<AmountByLabel[]> {
    return this.getReferenceList<AmountByLabel>('/reports/purchases/by-project', this.scopeRecord(scope));
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
