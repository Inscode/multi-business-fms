import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';


export interface AgingCustomerEntry {
  customerName: string;
  customerId: number | null;
  area: string | null;
  totalOutstanding: number;
  overdue: number;
  current: number;
  days31to60: number;
  days61to90: number;
  days91plus: number;
  billCount: number;
  oldestBillDate: string;
  lastPaymentDate: string | null;
  cashPending: number;
  cashFollowUp: number;
  cashUrgent: number;
  cashSerious: number;
}

export interface AgingAreaSummary {
  area: string;
  totalOutstanding: number;
  overdue: number;
  current: number;
  days31to60: number;
  days61to90: number;
  days91plus: number;
  cashPending: number;
  cashSerious: number;
  customerCount: number;
  billCount: number;
  customers: AgingCustomerEntry[];
}

export interface AgingReportResponse {
  grandTotalOutstanding: number;
  grandOverdue: number;
  grandCashPending: number;
  grandCashSerious: number;
  totalCustomers: number;
  totalBills: number;
  topCustomers: AgingCustomerEntry[];
  allCustomers: AgingCustomerEntry[];
  byArea: AgingAreaSummary[];
}

export interface BillSequenceGap {
  billSource: string;
  totalBills: number;
  firstNumber: number;
  lastNumber: number;
  missingCount: number;
  missingNumbers: string[];
}

export interface BillFilter {
  business?: string;
  status?: string;
  excludeCompleted?: boolean;
  from?: string;
  to?: string;
}

export interface BillResponse {
  /**
   * Collected on the hand-written bill for the same sale. This bill is real and its
   * stock went out; it is simply not the one being paid, so it leaves the aging report
   * and closes when that bill is paid off.
   */
  settledOnBillId?: number;
  settledOnBillNumber?: string;
  settledOnStatus?: string;
  settledOnNote?: string;
  settledOnBy?: string;

  /** Why it was voided. */
  cancelReason?: string;
  cancelledBy?: string;
  cancelledAt?: string;

  /** How it reached the customer, and the lorry round it travelled on. */
  deliveryMode?: 'UNSPECIFIED' | 'ROUTE' | 'IMMEDIATE' | 'STORE_PICKUP';
  deliveryRunId?: number;
  deliveryRunArea?: string;
  deliveryRunDate?: string;

  excludedFromAging?: boolean;
  agingExclusionReason?: string;
  agingExcludedBy?: string;
  agingExcludedAt?: string;

  id: number;
  billNumber: string;
  business: string;
  division: string;
  billType: string;
  billSource: string;
  customerName: string;
  /** Null on bills whose customer was only ever typed as a name. */
  customerId?: number;
  totalAmount: number;
  amountPaid: number;
  balanceRemaining: number;
  fullyPaid: boolean;
  status: string;
  workerId: number | null;
  workerName: string | null;
  enteredByName: string;
  receivedByName: string | null;
  receivedAt: string | null;
  area: string | null;
  billDate: string;
  notes: string | null;
  createdAt: string;
  willBeLinked?: boolean;
  stockCleared?: boolean;
  collectionOnly?: boolean;
}

export interface SkipReviewResponse {
  id: number;
  business: string;
  skippedBillNumber: string;
  relatedBillNumber: string | null;
  customerName: string | null;
  submittedByName: string | null;
  submittedAt: string;
}

/** A number offered in the create-bill dropdown. */
export interface BillNumberOption {
  number: number;
  /** Sits in a hole below the highest number used — never entered, and not an approved skip. */
  missing: boolean;
}

@Injectable({
  providedIn: 'root',
})
export class Bill {
  private apiUrl = `${environment.apiUrl}/bills`;

  constructor(private http: HttpClient) {}

  globalSearch(q: string): Observable<BillResponse[]> {
    return this.http.get<BillResponse[]>(`${this.apiUrl}/search`, { params: { q } });
  }

  getNextBillNumbers(business: string, billSource: string): Observable<BillNumberOption[]> {
    return this.http.get<BillNumberOption[]>(`${this.apiUrl}/next-numbers`,
      { params: { business, billSource } });
  }

  getPendingSkips(): Observable<SkipReviewResponse[]> {
    return this.http.get<SkipReviewResponse[]>(`${this.apiUrl}/skip-reviews`);
  }

  approveSkip(id: number): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/skip-reviews/${id}/approve`, {});
  }

  rejectSkip(id: number): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/skip-reviews/${id}/reject`, {});
  }

  getBills(filter?: BillFilter): Observable<BillResponse[]> {
    let params = new HttpParams();
    if (filter?.business) params = params.set('business', filter.business);
    if (filter?.status)   params = params.set('status', filter.status);
    if (filter?.excludeCompleted !== undefined) params = params.set('excludeCompleted', String(filter.excludeCompleted));
    if (filter?.from) params = params.set('from', filter.from);
    if (filter?.to)   params = params.set('to', filter.to);
    return this.http.get<BillResponse[]>(this.apiUrl, { params });
  }

  getLinkingBills(): Observable<BillResponse[]> {
    return this.http.get<BillResponse[]>(`${this.apiUrl}/linking`);
  }

  getOverdueCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.apiUrl}/overdue-count`);
  }

  getBillById(id: number): Observable<BillResponse> {
    return this.http.get<BillResponse>(`${this.apiUrl}/${id}`);
  }

  createBill(payload: any): Observable<BillResponse> {
    return this.http.post<BillResponse>(this.apiUrl, payload);
  }

  assignBill(id: number, workerId: number): Observable<BillResponse> {
    return this.http.patch<BillResponse>(`${this.apiUrl}/${id}/assign`, { workerId });
  }

  bulkAssignBills(billIds: number[], workerId: number): Observable<BillResponse[]> {
    return this.http.patch<BillResponse[]>(`${this.apiUrl}/bulk-assign`, { billIds, workerId });
  }

  bulkMarkReceived(billIds: number[]): Observable<BillResponse[]> {
    return this.http.patch<BillResponse[]>(`${this.apiUrl}/bulk-receive`, { billIds });
  }

  bulkMarkShopReceived(billIds: number[]): Observable<BillResponse[]> {
    return this.http.patch<BillResponse[]>(`${this.apiUrl}/bulk-shop-receive`, { billIds });
  }

  markReceived(id: number): Observable<BillResponse> {
    return this.http.patch<BillResponse>(`${this.apiUrl}/${id}/receive`, {});
  }

  updateBill(id: number, payload: any): Observable<BillResponse> {
    return this.http.put<BillResponse>(`${this.apiUrl}/${id}`, payload);
  }

  markShopReceived(id: number): Observable<BillResponse> {
    return this.http.patch<BillResponse>(`${this.apiUrl}/${id}/shop-receive`, {});
  }

  markStockCleared(id: number): Observable<BillResponse> {
    return this.http.patch<BillResponse>(`${this.apiUrl}/${id}/mark-stock-cleared`, {});
  }

  markCompleted(id: number): Observable<BillResponse> {
    return this.http.patch<BillResponse>(`${this.apiUrl}/${id}/complete`, {});
  }

  /**
   * Voids a bill. The reason is required: the bill keeps its number and stays in the
   * run forever, and whoever finds it later has only this to explain it.
   */
  cancelBill(id: number, reason: string): Observable<BillResponse> {
    return this.http.patch<BillResponse>(`${this.apiUrl}/${id}/cancel`, { reason });
  }

  /**
   * Records that this bill's money is collected on a hand-written one instead.
   *
   * <p>Used where a bill used to be cancelled as a duplicate. Cancelling said the sale
   * never happened; it did, and the stock went out on it. Linking keeps the record and
   * only stops the chasing.
   */
  linkSettlement(id: number, targetBillId: number, note?: string): Observable<BillResponse> {
    return this.http.patch<BillResponse>(`${this.apiUrl}/${id}/settle-on`, {
      targetBillId,
      note: note ?? '',
    });
  }

  unlinkSettlement(id: number): Observable<BillResponse> {
    return this.http.delete<BillResponse>(`${this.apiUrl}/${id}/settle-on`);
  }

  /** Manual bills this one could be collected on — same customer, within a month. */
  getSettleCandidates(id: number): Observable<BillResponse[]> {
    return this.http.get<BillResponse[]>(`${this.apiUrl}/${id}/settle-candidates`);
  }

  /** The bills collected on this one. */
  getSettledByBills(id: number): Observable<BillResponse[]> {
    return this.http.get<BillResponse[]>(`${this.apiUrl}/${id}/settled-by`);
  }

  toggleCollectionOnly(id: number): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/${id}/toggle-collection-only`, {});
  }

  deleteBill(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  getAgingReport(business: string = 'RAINCO'): Observable<AgingReportResponse> {
    return this.http.get<AgingReportResponse>(`${this.apiUrl}/aging-report`, {
      params: { business },
    });
  }

  /** Printable aging data — area and billType optional (omit = all). */
  getAgingExport(business: string, area?: string, billType?: string, sort?: string): Observable<AgingExport> {
    let params = new HttpParams().set('business', business);
    if (area) params = params.set('area', area);
    if (billType) params = params.set('billType', billType);
    if (sort) params = params.set('sort', sort);
    return this.http.get<AgingExport>(`${this.apiUrl}/aging-report/export`, { params });
  }

  downloadAgingExcel(business: string, area?: string, billType?: string, sort?: string): Observable<Blob> {
    let params = new HttpParams().set('business', business);
    if (area) params = params.set('area', area);
    if (billType) params = params.set('billType', billType);
    if (sort) params = params.set('sort', sort);
    return this.http.get(`${this.apiUrl}/aging-report/export.xlsx`, {
      params, responseType: 'blob',
    });
  }

  findSequenceGaps(business: string = 'RAINCO'): Observable<BillSequenceGap[]> {
    return this.http.get<BillSequenceGap[]>(`${this.apiUrl}/sequence-gaps`, {
      params: { business },
    });
  }

  // ── Bill Review ────────────────────────────────────────────────────────────

  getUnreviewedBills(): Observable<BillResponse[]> {
    return this.http.get<BillResponse[]>(`${this.apiUrl}/review/unreviewed`);
  }

  getUnreviewedCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.apiUrl}/review/unreviewed-count`);
  }

  markBillsReviewed(billIds: number[]): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/review/mark`, { billIds });
  }

  markAllBillsReviewed(): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/review/mark-all`, {});
  }

  /**
   * Hides a bill from the aging report, or puts it back. Admin only. Never a delete —
   * the balance stays owed, it just stops being reported as chaseable.
   */
  setAgingVisibility(billId: number, excluded: boolean, reason?: string): Observable<BillResponse> {
    return this.http.patch<BillResponse>(
      `${this.apiUrl}/${billId}/aging-visibility`, { excluded, reason });
  }

  /** Bills currently hidden, so an exclusion cannot be quietly forgotten. */
  getAgingExcluded(business: string): Observable<BillResponse[]> {
    return this.http.get<BillResponse[]>(`${this.apiUrl}/aging-report/excluded`,
      { params: new HttpParams().set('business', business) });
  }
}

export interface AgingExportBillRow {
  billNumber: string;
  billDate: string;
  ageDays: number;
  customerName: string;
  area: string | null;
  billType: string;
  totalAmount: number;
  amountPaid: number;
  balance: number;
  bucket: string;
  workerName: string | null;
  lastPaymentDate: string | null;
}

export interface AgingExportCustomer {
  customerName: string;
  area: string | null;
  totalOutstanding: number;
  overdue: number;
  current: number;
  days31to60: number;
  days61to90: number;
  days91plus: number;
  cashPending: number;
  cashFollowUp: number;
  cashUrgent: number;
  cashSerious: number;
  billCount: number;
  oldestBillDate: string | null;
  lastPaymentDate: string | null;
}

export interface AgingExport {
  business: string;
  area: string | null;
  billType: string | null;
  generatedOn: string;
  creditCustomers: AgingExportCustomer[];
  cashCustomers: AgingExportCustomer[];
  bills: AgingExportBillRow[];
  totalCredit: number;
  totalCash: number;
  totalOutstanding: number;
  customerCount: number;
  billCount: number;

  /** Bills an admin kept off this report, and what they come to. */
  excludedCount?: number;
  excludedAmount?: number;
}
