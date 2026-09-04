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

/**
 * Filters accepted by the list endpoints. Every field is optional; the backend applies the
 * subset that makes sense for the resource (PERF-02).
 */
export interface ListFilter {
  projectId?: number | null;
  clientId?: number | null;
  supplierId?: number | null;
  categoryId?: number | null;
  apartmentId?: number | null;
  paymentStatus?: string | null;
  paymentMethod?: string | null;
  /** Supplier-invoice settlement state, or `OVERDUE` for what is past due (UX-04). */
  settlement?: string | null;
  dateFrom?: string | null;
  dateTo?: string | null;
  search?: string | null;
}

/** A page request: zero-based page index, page size, and an optional `field,dir` sort. */
export interface PageQuery {
  page: number;
  size: number;
  sort?: string;
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
  projectName?: string;
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
  typeLabel?: string;
  active?: boolean;
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
  categoryName?: string;
  projectId?: number;
  projectName?: string;
  supplierId?: number | null;
  supplierName?: string;
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
  clientName?: string;
  projectId: number;
  projectName?: string;
  apartmentId?: number;
  apartmentNumber?: string;
  advanceAmount?: number;
  collectedAmount?: number;
  remainingAmount?: number;
  completionPercentage?: number;
  paymentStatus?: PurchasePaymentStatus;
  completed?: boolean;
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
  apartmentNumber?: string;
  clientId?: number;
  clientName?: string;
  projectId?: number;
  projectName?: string;
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

/** State of one instalment, derived by the backend from its due date and what was collected. */
export type InstallmentStatus = 'PAID' | 'PARTIALLY_PAID' | 'OVERDUE' | 'UPCOMING';

/** One line of a contract's payment schedule (UX-03). */
export interface PaymentInstallment {
  id: number;
  purchaseId: number;
  purchaseReference?: string;
  clientId?: number;
  clientName?: string;
  projectId?: number;
  projectName?: string;
  apartmentId?: number;
  apartmentNumber?: string;
  sequenceNo: number;
  label: string;
  dueDate: string;
  amount: number;
  /** Share of the money collected on the contract that this line absorbs. */
  settledAmount: number;
  remainingAmount: number;
  status: InstallmentStatus;
  daysLate: number;
  notes?: string;
}

/** A contract's schedule set against the money actually received. */
export interface PaymentSchedule {
  purchaseId: number;
  purchaseReference?: string;
  contractAmount: number;
  scheduledAmount: number;
  /** Contract total minus what the plan covers; zero for a complete plan. */
  unscheduledAmount: number;
  collectedAmount: number;
  overdueAmount: number;
  installments: PaymentInstallment[];
}

/** One line submitted when saving a schedule by hand. */
export interface InstallmentLine {
  label: string;
  dueDate: string;
  amount: number;
  notes?: string;
}

/** Percentage template used to generate a schedule. */
export interface ScheduleTemplate {
  firstDueDate: string;
  intervalMonths: number;
  lines: { label?: string; percentage: number }[];
}

/** What is late and what falls due this month. */
export interface InstallmentSummary {
  overdueCount: number;
  overdueAmount: number;
  dueThisMonthCount: number;
  dueThisMonthAmount: number;
  scheduledAmount: number;
  collectedAmount: number;
}

/** The documents a proof file can be attached to (FE-05). */
export type AttachmentOwnerType = 'EXPENSE' | 'CLIENT_ADVANCE' | 'CLIENT_PURCHASE' | 'SUPPLIER_INVOICE';

/** A stored proof file. Its bytes are served by GET /api/attachments/{id}. */
export interface Attachment {
  id: number;
  originalName: string;
  contentType: string;
  sizeBytes: number;
  uploadedBy: string;
  uploadedAt: string;
  ownerType: AttachmentOwnerType;
  ownerId: number;
}

export interface AuditLog {
  id: number;
  entityType: string;
  entityId: number;
  action: string;
  summary: string;
  /** Who performed the action; 'system' while the application has no login. */
  actor?: string;
  createdAt?: string;
}

/** Where a unit stands commercially (UX-05). */
export type SalesStatus = 'AVAILABLE' | 'RESERVED' | 'SOLD' | 'DELIVERED';

/** One unit as it appears on the sales board. */
export interface SalesBoardUnit {
  id: number;
  apartmentNumber: string;
  apartmentType: string;
  totalSurface: number;
  totalSalePrice: number;
  salesStatus: SalesStatus;
  acquirerName?: string;
  contractedAmount: number;
  collectedAmount: number;
  remainingAmount: number;
}

export interface SalesBoardFloor {
  floorNumber?: number | null;
  label: string;
  units: SalesBoardUnit[];
}

export interface SalesBoardBlock {
  block: string;
  unitCount: number;
  availableCount: number;
  floors: SalesBoardFloor[];
}

/** The commercial state of a project's stock. */
export interface SalesBoard {
  unitCount: number;
  availableCount: number;
  reservedCount: number;
  soldCount: number;
  deliveredCount: number;
  inventoryValue: number;
  placedValue: number;
  availableValue: number;
  contractedAmount: number;
  collectedAmount: number;
  remainingAmount: number;
  blocks: SalesBoardBlock[];
}

export interface Apartment {
  id?: number;
  /** Set by the backend; changed through its own endpoint, never through the form. */
  salesStatus?: SalesStatus;
  block?: string | null;
  floorNumber?: number | null;
  apartmentNumber: string;
  apartmentType: string;
  totalSurface: number;
  gardenSurface?: number;
  parkingCount?: string;
  cellarCount?: number;
  totalSalePrice?: number;
  detail?: string;
  projectId?: number;
  projectName?: string;
  acquirerId?: number | null;
  acquirerName?: string;
  /** Derived by the backend from the apartment's sale contract and its advances. */
  totalPurchases?: number;
  totalAdvances?: number;
  totalCollected?: number;
  remainingToCollect?: number;
}

/** How far a supplier invoice has been settled (UX-04). */
export type SettlementStatus = 'UNPAID' | 'PARTIALLY_PAID' | 'PAID';

/** A payment made against a supplier invoice. */
export interface SupplierPayment {
  id?: number;
  invoiceId?: number;
  invoiceNumber?: string;
  paymentDate: string;
  amount: number;
  paymentMethod?: string;
  reference?: string;
  notes?: string;
}

/** What the promoter owes suppliers. */
export interface PayablesSummary {
  invoiceCount: number;
  invoicedAmount: number;
  paidAmount: number;
  dueAmount: number;
  overdueCount: number;
  overdueAmount: number;
}

export interface SupplierInvoice {
  id?: number;
  invoiceNumber: string;
  invoiceDate: string;
  /** When the supplier expects payment; without it the invoice can never be late. */
  dueDate?: string | null;
  amountHt: number;
  /** VAT rate applied, as a fraction. Sent to the backend, which derives the amounts below. */
  vatRate: number;
  vatAmount?: number;
  amountTtc?: number;
  /** Derived by the backend from the payments recorded against the invoice. */
  paidAmount?: number;
  remainingAmount?: number;
  status?: SettlementStatus;
  overdue?: boolean;
  daysLate?: number;
  attachmentName?: string;
  attachmentUrl?: string;
  detail?: string;
  supplierId?: number;
  supplierName?: string;
  projectId?: number;
  projectName?: string;
}
