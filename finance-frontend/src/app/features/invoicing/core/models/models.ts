export type CategoryType = 'RAINCO' | 'STATIONERY' | 'PLASTIC';
export type InvoiceMethod = 'MIX' | 'RAINCO_ONLY' | 'STATIONERY_ONLY' | 'PLASTIC_ONLY';
export type InvoiceType = 'CASH' | 'CREDIT';
export type ReturnType = 'DAMAGE' | 'SALABLE';
export type ReturnStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type GrnStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type UserRole = 'ADMIN' | 'STAFF';

export interface Customer {
  id: number;
  customerCode?: string;
  name: string;
  address?: string;
  customerType?: string;
  active: boolean;
  phone?: string;
  city?: string;
  tier?: string;
  category?: string;
  reviewed?: boolean;
}

export interface Brand {
  id: number;
  name: string;
  brandCode?: string;
  category: CategoryType;
  discountType: string;
  defaultMarginPct?: number;
  active: boolean;
  slabs: DiscountSlab[];
}

export interface DiscountSlab {
  id: number;
  minValue?: number;
  maxValue?: number;
  discountPct: number;
  sortOrder?: number;
}

export interface Item {
  id: number;
  itemCode: string;
  description: string;
  category: CategoryType;
  brandId: number;
  brandName: string;
  mrp?: number;
  marginPct?: number;
  wsp?: number;
  wholesalePrice?: number;
  active: boolean;
  stockQty: number;
  /** Automatic scheme: buy this many, get freeIssueFreeQty free. */
  freeIssueBuyQty?: number;
  freeIssueFreeQty?: number;
}

export interface InvoiceLine {
  id: number;
  itemId: number;
  itemCode: string;
  itemDescription: string;
  brandId: number;
  brandName: string;
  qty: number;
  /** Given free — no value, but deducted from stock. */
  freeQty?: number;
  mrp: number;
  marginPct?: number;
  wsp: number;
  value: number;
  appliedDiscountPct?: number;
  sortOrder?: number;
}

export interface Invoice {
  id: number;
  invoiceNo: string;
  externalRef?: string;
  method: InvoiceMethod;
  invoiceDate: string;
  customerId: number;
  customerName: string;
  customerAddress?: string;
  invoiceType: InvoiceType;
  grossTotal: number;
  totalSlabDiscount: number;
  cashDiscountPct?: number;
  cashDiscountAmount: number;
  plasticDiscountPct?: number;
  /** Flat admin rate replacing the slab, for promotions. */
  discountOverridePct?: number | null;
  discountOverrideBy?: string;
  plasticDiscountAmount?: number;
  netTotal: number;
  agentPrintedNet?: number;
  variance?: number;
  printedBy?: string;
  createdAt: string;
  duplicatePrint: boolean;
  /** Bill raised in the bills section, where payments are collected. */
  billId?: number | null;
  /** Attached to a bill already entered by hand — stock moved, no second bill. */
  billLinkedExisting?: boolean;
  lines: InvoiceLine[];
}

export interface InvoiceSummary {
  /**
   * Voided but kept. The number was issued and the goods moved, so both facts need
   * somewhere to live — an invoice simply erased takes the record of the mistake with it.
   */
  cancelled?: boolean;
  cancelReason?: string;
  cancelledBy?: string;

  id: number;
  invoiceNo: string;
  externalRef?: string;
  method: InvoiceMethod;
  invoiceDate: string;
  customerName: string;
  invoiceType: InvoiceType;
  grossTotal: number;
  totalDiscount: number;
  cashDiscountAmount: number;
  netTotal: number;
  duplicatePrint: boolean;
  /** Bill raised in the bills section, where payments are collected. */
  billId?: number | null;
  /** Attached to a bill already entered by hand — stock moved, no second bill. */
  billLinkedExisting?: boolean;
}

export interface ReturnItem {
  id?: number;
  itemId?: number;
  itemName?: string;
  unitPrice?: number;
  qtyRequested: number;
  qtyReturned?: number;
  lineTotal?: number;
}

export interface Return {
  id: number;
  invoiceId: number;
  invoiceNo: string;
  customerName: string;
  category: CategoryType;
  returnType: ReturnType;
  status: ReturnStatus;
  itemsTotal?: number;
  discountPct?: number;
  discountFixed?: number;
  calculatedReturnAmount?: number;
  approvedAmount?: number;
  rejectionReason?: string;
  notes?: string;
  submittedBy?: string;
  reviewedBy?: string;
  reviewedAt?: string;
  createdAt: string;
  items: ReturnItem[];
}

export interface InvoicePrintLine {
  itemCode: string;
  description: string;
  qty: number;
  freeIssueQty?: number;
  mrp: number;
  marginPct?: number;
  wsp: number;
  value: number;
}

export interface InvoicePrintBrandGroup {
  brandId: number;
  brandName: string;
  lines: InvoicePrintLine[];
  brandTotalWsp: number;
  brandDiscountPct: number;
  brandDiscountAmount: number;
  brandNetTotal: number;
}

export interface InvoicePrint {
  watermark: string;
  companyName: string;
  companyAddress: string;
  companyCity: string;
  companyTel: string;
  distributorLine: string;
  invoiceNo: string;
  externalRef?: string;
  invoiceDate: string;
  method: InvoiceMethod;
  invoiceType: InvoiceType;
  customerCode: string;
  customerName: string;
  customerAddress?: string;
  customerPhone?: string;
  brandGroups: InvoicePrintBrandGroup[];
  grossTotal: number;
  totalSlabDiscount: number;
  cashDiscountPct?: number;
  cashDiscountAmount: number;
  netTotal: number;
}

export interface Supplier {
  id: number;
  name: string;
  category: CategoryType;
  contactInfo?: string;
  active: boolean;
}

export interface GrnLine {
  id?: number;
  itemId: number;
  itemCode?: string;
  itemDescription?: string;
  qty: number;
  unitCost?: number;
  lineTotal?: number;
}

export interface Grn {
  id: number;
  grnNo: string;
  supplierId: number;
  supplierName: string;
  category: CategoryType;
  receivedDate: string;
  status: GrnStatus;
  rejectionReason?: string;
  notes?: string;
  submittedBy?: string;
  reviewedBy?: string;
  reviewedAt?: string;
  createdAt: string;
  lines: GrnLine[];
}

export interface AppUser {
  id: number;
  username: string;
  role: UserRole;
  active: boolean;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}


/** What a draft invoice would come to, priced by the server before anything is saved. */
export interface QuoteBrandGroup {
  brandId: number;
  brandName: string;
  gross: number;
  discountPct: number;
  discountAmount: number;
  net: number;
  /** The next slab up, when a better rate is within reach. */
  nextSlabAt?: number | null;
  nextSlabPct?: number | null;
  amountToNextSlab?: number | null;
}

export interface Quote {
  brandGroups: QuoteBrandGroup[];
  grossTotal: number;
  totalSlabDiscount: number;
  cashDiscountPct?: number | null;
  cashDiscountAmount: number;
  plasticDiscount: number;
  netTotal: number;
  totalDiscount: number;
  totalFreeQty: number;
}
