export interface DashboardSummary {
  totalExpenses: number;
  totalAdvances: number;
  totalPurchases: number;
  totalRemainingFromClients?: number;
  clients?: number;
  projects?: number;
  suppliers?: number;
  expenses?: number;
  clientAdvances?: number;
  clientPurchases?: number;
  /** Scope the figures cover, resolved by the backend. */
  projectId?: number | null;
  projectLabel?: string;
  periodLabel?: string;
}

/**
 * Scope sent to every report endpoint. `projectId` absent means "the active project context";
 * `'ALL'` aggregates across projects.
 */
export interface ReportScopeParams {
  projectId?: number | 'ALL' | null;
  year?: number | null;
  month?: number | null;
}

export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface ExpenseCategory {
  id: number;
  name: string;
}

export interface SupplierTypeOption {
  id: number;
  label: string;
  active?: boolean;
}

/** A VAT rate offered at data entry. `rate` is a fraction: 0.19 for 19 %. */
export interface VatRateOption {
  id: number;
  label: string;
  rate: number;
  active?: boolean;
}

export interface Project {
  id?: number;
  code?: string;
  name: string;
  location?: string;
  description?: string;
  startDate?: string;
  expectedEndDate?: string;
  budget?: number;
  status?: string;
  activeContext?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface Client {
  id?: number;
  fullName: string;
  phone?: string;
  email?: string;
  address?: string;
  cinOrFiscalId?: string;
  notes?: string;
  active?: boolean;
  projectId?: number;
  project?: Project;
}

export interface Supplier {
  id?: number;
  name: string;
  fiscalId?: string;
  phone?: string;
  email?: string;
  address?: string;
  /** Rate this supplier usually invoices, as a fraction. Proposed by default at data entry. */
  defaultVatRate?: number | null;
  typeId?: number | null;
  type?: SupplierTypeOption | null;
  active?: boolean;
  createdAt?: string;
  updatedAt?: string;
}


export interface Expense {
  id?: number;
  reference?: string;
  description: string;
  expenseDate: string;
  amountHt: number;
  /** VAT rate applied, as a fraction. Sent to the backend, which derives the amounts below. */
  vatRate: number;
  vatAmount?: number;
  amountTtc?: number;
  paymentMethod?: string;
  documentNumber?: string;
  attachmentName?: string;
  attachmentUrl?: string;
  notes?: string;
  categoryId?: number;
  projectId?: number;
  supplierId?: number | null;
  category?: ExpenseCategory;
  project?: Project;
  supplier?: Supplier | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface ClientPurchase {
  id?: number;
  reference?: string;
  purchaseDate: string;
  contractDate?: string;
  totalAmount: number;
  paidAmount?: number;
  assetDescription: string;
  attachmentName?: string;
  attachmentUrl?: string;
  notes?: string;
  clientId: number;
  projectId: number;
  apartmentId?: number;
  client?: Client;
  project?: Project;
  apartment?: Apartment;
  advanceAmount?: number;
  collectedAmount?: number;
  remainingAmount?: number;
  completionPercentage?: number;
  paymentStatus?: PurchasePaymentStatus;
  completed?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface ClientAdvance {
  id?: number;
  reference?: string;
  advanceDate: string;
  amount: number;
  paymentMethod?: string;
  attachmentName?: string;
  attachmentUrl?: string;
  notes?: string;
  apartmentId?: number;
  clientId?: number;
  projectId?: number;
  apartment?: Apartment;
  client?: Client;
  project?: Project;
  createdAt?: string;
  updatedAt?: string;
}

export interface ClientStatement {
  clientId: number;
  clientName: string;
  totalPurchases: number;
  totalAdvances: number;
  remainingToPay: number;
}

export interface AmountByLabel {
  label: string;
  amount: number;
}

export type PurchasePaymentStatus = 'UNPAID' | 'PARTIALLY_PAID' | 'PAID';

export interface AuditLog {
  id: number;
  entityType: string;
  entityId: number;
  action: string;
  summary: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface Apartment {
  id?: number;
  apartmentNumber: string;
  apartmentType: string;
  totalSurface: number;
  gardenSurface?: number;
  parkingCount?: string;
  cellarCount?: number;
  totalSalePrice?: number;
  detail?: string;
  projectId?: number;
  acquirerId?: number | null;
  project?: Project;
  acquirer?: Client | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface SupplierInvoice {
  id?: number;
  invoiceNumber: string;
  invoiceDate: string;
  amountHt: number;
  /** VAT rate applied, as a fraction. Sent to the backend, which derives the amounts below. */
  vatRate: number;
  vatAmount?: number;
  amountTtc?: number;
  attachmentName?: string;
  attachmentUrl?: string;
  detail?: string;
  supplierId?: number;
  projectId?: number;
  supplier?: Supplier;
  project?: Project;
  createdAt?: string;
  updatedAt?: string;
}
