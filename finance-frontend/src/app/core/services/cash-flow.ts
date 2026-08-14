import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface BusinessCashFlow {
  business: string;
  /** Cheques already in hand, dated inside the window. */
  chequesIncoming: number;
  chequeCount: number;
  purchasesDue: number;
  payablesDue: number;
  totalOutgoing: number;
  net: number;
  overdueOutgoing: number;
  /** Owed by customers with no date — context, not counted in net. */
  undatedReceivable: number;
  /** Payable GRNs with no terms typed — owed, but not in the figures above. */
  untermed: number;
  untermedCount: number;
}

export interface CashFlowEntry {
  date: string;
  business: string;
  direction: 'IN' | 'OUT';
  source: 'CHEQUE' | 'GRN' | 'PAYABLE';
  reference: string | null;
  party: string | null;
  amount: number;
  overdue: boolean;
}

export interface CashFlowForecast {
  from: string;
  to: string;
  horizonDays: number;
  businesses: BusinessCashFlow[];
  totalIncoming: number;
  totalOutgoing: number;
  totalNet: number;
  totalUndatedReceivable: number;
  totalOverdueOutgoing: number;
  totalUntermed: number;
  untermedCount: number;
}

export interface SupplierPayable {
  id: number;
  business: string;
  supplierName: string | null;
  description: string;
  amount: number;
  dueDate: string;
  chequeNumber: string | null;
  bankName: string | null;
  settled: boolean;
  settledOn: string | null;
  notes: string | null;
  createdByName: string | null;
  daysUntilDue: number;
}

export interface SupplierPayableRequest {
  business: string;
  supplierName?: string;
  description: string;
  amount: number;
  dueDate: string;
  chequeNumber?: string;
  bankName?: string;
  notes?: string;
}

@Injectable({ providedIn: 'root' })
export class CashFlowService {
  private base = `${environment.apiUrl}/cash-flow`;
  private payables = `${environment.apiUrl}/supplier-payables`;

  constructor(private http: HttpClient) {}

  forecast(days: number): Observable<CashFlowForecast> {
    return this.http.get<CashFlowForecast>(`${this.base}/forecast`, {
      params: new HttpParams().set('days', days),
    });
  }

  entries(days: number): Observable<CashFlowEntry[]> {
    return this.http.get<CashFlowEntry[]>(`${this.base}/entries`, {
      params: new HttpParams().set('days', days),
    });
  }

  // ── Obligations with no GRN in this system ──────────────────────
  listPayables(): Observable<SupplierPayable[]> {
    return this.http.get<SupplierPayable[]>(this.payables);
  }

  createPayable(req: SupplierPayableRequest): Observable<SupplierPayable> {
    return this.http.post<SupplierPayable>(this.payables, req);
  }

  settlePayable(id: number, settled: boolean): Observable<SupplierPayable> {
    return this.http.patch<SupplierPayable>(`${this.payables}/${id}/settle`, {}, {
      params: new HttpParams().set('settled', settled),
    });
  }

  deletePayable(id: number): Observable<void> {
    return this.http.delete<void>(`${this.payables}/${id}`);
  }
}
