import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type VisitStatus = 'NOT_VISITED' | 'SHOP_CLOSED' | 'REVISIT_REQUESTED' | 'DELIVERED_PENDING' | 'DELIVERED_PAID';
export type WPaymentStatus = 'PENDING' | 'OWNER_CONFIRMED' | 'REJECTED';

export interface WorkerBill {
  id: number;
  billNumber: string;
  customerName: string;
  area: string | null;
  business: string;
  totalAmount: number;
  balanceRemaining: number;
  tempBalance: number;
  workerPendingTotal: number;
  billDate: string;
  workerName: string | null;
  visitStatus: VisitStatus;
  visitNote: string | null;
  managementNotes: string[];
  workerPayments: WorkerPaymentEntry[];
}

export interface WorkerPaymentEntry {
  id: number;
  billId: number;
  billNumber: string;
  customerName: string;
  groupId: number | null;
  workerName: string;
  amount: number;
  paymentType: 'CASH' | 'CHEQUE';
  chequeNumber: string | null;
  bankName: string | null;
  branchName: string | null;
  status: WPaymentStatus;
  workerNote: string | null;
  enteredAt: string;
  confirmedAt: string | null;
  confirmedByName: string | null;
  rejectedReason: string | null;
}

export interface WorkerPaymentGroup {
  id: number;
  workerName: string;
  paymentType: 'CASH' | 'CHEQUE';
  chequeNumber: string | null;
  bankName: string | null;
  totalAmount: number;
  status: WPaymentStatus;
  workerNote: string | null;
  createdAt: string;
  entries: WorkerPaymentEntry[];
}

@Injectable({ providedIn: 'root' })
export class WorkerPortalService {
  private base = `${environment.apiUrl}/worker`;
  private collectBase = `${environment.apiUrl}/worker-collections`;

  constructor(private http: HttpClient) {}

  // Worker-facing
  getBills(): Observable<WorkerBill[]> {
    return this.http.get<WorkerBill[]>(`${this.base}/bills`);
  }

  getBillDetail(billId: number): Observable<WorkerBill> {
    return this.http.get<WorkerBill>(`${this.base}/bills/${billId}`);
  }

  markVisit(billId: number, visitStatus: VisitStatus, workerNote?: string): Observable<void> {
    return this.http.post<void>(`${this.base}/bills/${billId}/visit`, { visitStatus, workerNote });
  }

  enterPayment(req: {
    billId: number; amount: number; paymentType: 'CASH' | 'CHEQUE';
    chequeNumber?: string; bankName?: string; branchName?: string; workerNote?: string;
  }): Observable<WorkerPaymentEntry> {
    return this.http.post<WorkerPaymentEntry>(`${this.base}/payments`, req);
  }

  enterGroupPayment(req: {
    paymentType: 'CASH' | 'CHEQUE';
    chequeNumber?: string; bankName?: string; branchName?: string; workerNote?: string;
    bills: { billId: number; amount: number }[];
  }): Observable<WorkerPaymentGroup> {
    return this.http.post<WorkerPaymentGroup>(`${this.base}/payments/group`, req);
  }

  editPayment(entryId: number, req: {
    billId: number; amount: number; paymentType: 'CASH' | 'CHEQUE';
    chequeNumber?: string; bankName?: string; branchName?: string; workerNote?: string;
  }): Observable<WorkerPaymentEntry> {
    return this.http.put<WorkerPaymentEntry>(`${this.base}/payments/${entryId}`, req);
  }

  deletePayment(entryId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/payments/${entryId}`);
  }

  getTodayPayments(): Observable<WorkerPaymentEntry[]> {
    return this.http.get<WorkerPaymentEntry[]>(`${this.base}/payments/today`);
  }

  // Owner/Admin/Acc-facing
  getAllCollections(): Observable<WorkerPaymentEntry[]> {
    return this.http.get<WorkerPaymentEntry[]>(this.collectBase);
  }

  getPendingCollections(): Observable<WorkerPaymentEntry[]> {
    return this.http.get<WorkerPaymentEntry[]>(`${this.collectBase}/pending`);
  }

  getWorkerBills(): Observable<WorkerBill[]> {
    return this.http.get<WorkerBill[]>(`${this.collectBase}/bills`);
  }

  confirmEntry(entryId: number): Observable<void> {
    return this.http.post<void>(`${this.collectBase}/${entryId}/confirm`, {});
  }

  confirmGroup(groupId: number): Observable<void> {
    return this.http.post<void>(`${this.collectBase}/group/${groupId}/confirm`, {});
  }

  rejectEntry(entryId: number, reason: string): Observable<void> {
    return this.http.post<void>(`${this.collectBase}/${entryId}/reject`, { reason });
  }
}
