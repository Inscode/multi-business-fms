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
  id: number;
  billNumber: string;
  business: string;
  division: string;
  billType: string;
  billSource: string;
  customerName: string;
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

@Injectable({
  providedIn: 'root',
})
export class Bill {
  private apiUrl = `${environment.apiUrl}/bills`;

  constructor(private http: HttpClient) {}

  globalSearch(q: string): Observable<BillResponse[]> {
    return this.http.get<BillResponse[]>(`${this.apiUrl}/search`, { params: { q } });
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

  cancelBill(id: number): Observable<BillResponse> {
    return this.http.patch<BillResponse>(`${this.apiUrl}/${id}/cancel`, {});
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

  findSequenceGaps(business: string = 'RAINCO'): Observable<BillSequenceGap[]> {
    return this.http.get<BillSequenceGap[]>(`${this.apiUrl}/sequence-gaps`, {
      params: { business },
    });
  }
}