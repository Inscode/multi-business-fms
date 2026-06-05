import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

// TODO: Replace with actual types from backend DTOs

export interface ReturnProductResponse {
  id: number;
  name: string;
  unitPrice: number;
  business: string;
  active: boolean;
}

export interface ShadowStockMovement {
  id: number;
  productId: number;
  productName: string;
  type: 'STOCK_IN' | 'BILL_OUT' | 'SALABLE_RETURN' | 'DAMAGE_IN' | 'DAMAGE_TO_COMPANY';
  quantity: number;
  billId?: number;
  invoiceNumber?: string;
  date: string;
  notes?: string;
  enteredBy: string;
  cancelled: boolean;
}

export interface StockItemRequest {
  productId: number;
  quantity: number;
}

export interface BillStockItem {
  id: number;
  billId: number;
  productId: number;
  productName: string;
  quantity: number;
  unitPrice: number;
  lineTotal?: number;
}

export interface StockBillListItem {
  id: number;
  billNumber: string;
  billSource: string;
  customerName: string;
  amount: number;
  billDate: string;
  enteredByName: string;
  totalQty: number;
  isLinkingBill?: boolean;
}

export interface SummaryLoadBill {
  id: number;
  systemBillIds: number[];
  numberOfBills: number;
  totalQuantity: number;
  loadDate: string;
  notes?: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  createdByName: string;
  createdAt: string;
  approvedByName?: string;
  approvedAt?: string;
}

export interface StockReductionStatus {
  billId: number;
  billNumber: string;
  billSource: 'SYSTEM' | 'DRAFT' | 'MANUAL';
  customerName: string;
  amount: number;
  billDate: string;
  reductionStatus: 'NOT_REDUCED' | 'SUMMARY_PENDING' | 'SUMMARY_APPROVED' | 'INDIVIDUALLY_REDUCED' | 'LINKED' | 'WILL_LINK' | 'RECONCILED';
  totalQty?: number;
  summaryLoadBillId?: number;
  enteredByName: string;
  stockReconciled?: boolean;
  childrenTotalAmount?: number;
  savingsAmount?: number;
}

export interface IndividualStockReductionRequest {
  billId: number;
  items: StockItemRequest[];
  notes?: string;
}

export interface StockBillRequest {
  billSource: 'SYSTEM' | 'DRAFT' | 'MANUAL';
  billDate: string;
  items: StockItemRequest[];
  calculatedAmount: number;
  actualAmount?: number;
  notes?: string;
}

export interface BillLinkingRequest {
  draftManualBillIds: number[];
  systemBillId?: number;
  systemBillNumber?: string;
  notes?: string;
}

export interface SummaryItemInfo {
  productId: number;
  productName: string;
  quantity: number;
}

export interface SiblingBillInfo {
  billId: number;
  billNumber: string;
  customerName: string;
  amount: number;
  totalQty: number;
}

export interface LinkedChildInfo {
  billId: number;
  billNumber: string;
  billSource: string;
  customerName: string;
  amount: number;
  items: BillStockItem[];
  linkedByName: string;
  linkedAt: string;
  notes?: string;
}

export interface ItemComparisonRow {
  productId: number;
  productName: string;
  systemQty: number;
  childQty: number;
  diff: number;
}

export interface LinkedParentInfo {
  billId: number;
  billNumber: string;
  customerName: string;
  amount: number;
  systemItems: BillStockItem[];
  comparison: ItemComparisonRow[];
  linkedByName: string;
  linkedAt: string;
  notes?: string;
}

export interface BillStockStatus {
  billId: number;
  billNumber: string;
  billSource: string;
  reductionStatus: string;
  ownItems: BillStockItem[];
  // reconciliation fields (SYSTEM linking bills)
  stockReconciled?: boolean;
  quantitiesMatch?: boolean;
  childrenTotalAmount?: number;
  savingsAmount?: number;
  // summary context
  summaryLoadId?: number;
  summaryStatus?: string;
  summaryCreatedByName?: string;
  summaryLoadDate?: string;
  summaryItems?: SummaryItemInfo[];
  summaryRelatedBills?: SiblingBillInfo[];
  // linked children (SYSTEM bill with DRAFT/MANUAL)
  linkedChildren?: LinkedChildInfo[];
  // per-product: system ref items vs sum of all linked children (SYSTEM linking bill only)
  childrenAggregateComparison?: ItemComparisonRow[];
  // linked parent (DRAFT/MANUAL → SYSTEM)
  linkedParent?: LinkedParentInfo;
}

@Injectable({
  providedIn: 'root',
})
export class StockService {
  private apiUrl = `${environment.apiUrl}/stock`;

  constructor(private http: HttpClient) {}

  // Get products for RAINCO (for product picker)
  getRaincoProducts(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/products`);
  }

  // Search products by name
  searchProducts(query: string): Observable<ReturnProductResponse[]> {
    let params = new HttpParams();
    if (query) {
      params = params.set('query', query);
    }
    return this.http.get<ReturnProductResponse[]>(`${this.apiUrl}/products/search`, {
      params,
    });
  }

  // Get unassigned SYSTEM bills (for summary load)
  getUnassignedSystemBills(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/bills/unassigned`);
  }

  // Bills not yet reduced (for Stock Item Entry)
  getBillsNotYetReduced(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/bills/not-reduced`);
  }

  // All unlinked draft/manual bills (pending linking dashboard)
  getUnlinkedDashboard(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/bills/unlinked-dashboard`);
  }

  // Create a SYSTEM bill flagged as willBeLinked=true
  createLinkingSystemBill(req: {
    billNumber: string; customerName: string;
    amount: number; billDate: string; notes?: string;
  }): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/bills/linking-system-bill`, req);
  }

  // Stock reduction status for all RAINCO bills
  getStockReductionStatus(): Observable<StockReductionStatus[]> {
    return this.http.get<StockReductionStatus[]>(`${this.apiUrl}/reduction-status`);
  }

  // Reduce stock individually for a specific existing bill
  reduceStockForBill(request: IndividualStockReductionRequest): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/bills/reduce`, request);
  }

  // Get all summary load bills (history)
  getSummaryLoadBills(): Observable<SummaryLoadBill[]> {
    return this.http.get<SummaryLoadBill[]>(`${this.apiUrl}/summary-load`);
  }

  // Create summary load bill
  createSummaryLoadBill(request: any): Observable<SummaryLoadBill> {
    return this.http.post<SummaryLoadBill>(`${this.apiUrl}/summary-load`, request);
  }

  // Approve summary load bill (ADMIN only)
  approveSummaryLoadBill(id: number): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/summary-load/${id}/approve`, {});
  }

  // Reject summary load bill (ADMIN only)
  rejectSummaryLoadBill(id: number): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/summary-load/${id}/reject`, {});
  }

  // Create stock bill (DRAFT, MANUAL, or SYSTEM)
  createStockBill(request: StockBillRequest): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/bills`, request);
  }

  // Get unlinked DRAFT and MANUAL bills
  getUnlinkedBills(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/bills/unlinked`);
  }

  // Get available SYSTEM bills for linking
  getAvailableSystemBillsForLinking(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/bills/available-for-linking`);
  }

  // Link DRAFT/MANUAL bills to a SYSTEM bill
  linkBills(request: BillLinkingRequest): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/bills/link`, request);
  }

  // Get shadow stock movements
  getShadowStockMovements(productId?: number): Observable<ShadowStockMovement[]> {
    let params = new HttpParams();
    if (productId) {
      params = params.set('productId', productId);
    }
    return this.http.get<ShadowStockMovement[]>(`${this.apiUrl}/movements`, {
      params,
    });
  }

  // Get shadow stock balance
  getShadowStockBalance(): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/balance`);
  }

  // Get full stock status for a specific bill (for bill detail page)
  getBillStockStatus(billId: number): Observable<BillStockStatus> {
    return this.http.get<BillStockStatus>(`${this.apiUrl}/bills/${billId}/stock-status`);
  }

  // Get stock items for a specific bill
  getBillItems(billId: number): Observable<BillStockItem[]> {
    return this.http.get<BillStockItem[]>(`${this.apiUrl}/bills/${billId}/items`);
  }

  // Enter reference items on a SYSTEM linking bill (no stock movement — reconciliation only)
  enterReferenceItems(billId: number, items: { productId: number; quantity: number }[]): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/bills/${billId}/reference-items`, { items });
  }

  // Admin marks a SYSTEM linking bill as stock-reconciled
  reconcileBill(billId: number): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/bills/${billId}/reconcile`, {});
  }
}
