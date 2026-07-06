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
  collectionOnly: boolean;
  createdAt: string;
}

export interface WorkerPaymentEntry {
  id: number;
  billId: number;
  billNumber: string;
  customerName: string;
  groupId: number | null;
  workerName: string;
  amount: number;
  paymentType: 'CASH' | 'CHEQUE' | 'BANK_TRANSFER';
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
  paymentType: 'CASH' | 'CHEQUE' | 'BANK_TRANSFER';
  chequeNumber: string | null;
  bankName: string | null;
  totalAmount: number;
  status: WPaymentStatus;
  workerNote: string | null;
  createdAt: string;
  entries: WorkerPaymentEntry[];
}

export interface WorkerBillOverview {
  id: number;
  billNumber: string;
  customerName: string;
  area: string | null;
  status: string;
  business: string;
  billType: string;
  billDate: string;
  totalAmount: number;
  balanceRemaining: number;
  workerName: string | null;
  collectionOnly: boolean;
  createdAt: string;
}

export interface WorkerReturnOverview {
  id: number;
  billId: number;
  billNumber: string;
  customerName: string;
  area: string | null;
  returnType: 'DAMAGE' | 'SALABLE';
  status: string;
  itemsTotal: number;
  calculatedReturnAmount: number;
  notes: string | null;
  responsibleWorkerName: string | null;
  submittedAt: string;
}

export interface WorkerReminderOverview {
  id: number;
  billId: number;
  billNumber: string;
  customerName: string;
  area: string | null;
  reminderDate: string;
  period: string;
  note: string | null;
  status: string;
  createdAt: string;
}

export interface CollectionNote {
  id: number;
  billId: number;
  billNumber: string;
  customerName: string;
  area: string | null;
  billBalance: number;
  amount: number;
  paymentType: 'CASH' | 'CHEQUE' | 'BANK_TRANSFER';
  status: 'PENDING' | 'MATCHED';
  collectedByName: string;
  collectedAt: string;
  notes: string | null;
  chequeNumber: string | null;
  bankName: string | null;
  branchName: string | null;
  chequeDate: string | null;
  sourceEntryId: number | null;
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
    billId: number; amount: number; paymentType: 'CASH' | 'CHEQUE' | 'BANK_TRANSFER';
    chequeNumber?: string; bankName?: string; branchName?: string; workerNote?: string;
  }): Observable<WorkerPaymentEntry> {
    return this.http.post<WorkerPaymentEntry>(`${this.base}/payments`, req);
  }

  enterGroupPayment(req: {
    paymentType: 'CASH' | 'CHEQUE' | 'BANK_TRANSFER';
    chequeNumber?: string; bankName?: string; branchName?: string; workerNote?: string;
    bills: { billId: number; amount: number }[];
  }): Observable<WorkerPaymentGroup> {
    return this.http.post<WorkerPaymentGroup>(`${this.base}/payments/group`, req);
  }

  editPayment(entryId: number, req: {
    billId: number; amount: number; paymentType: 'CASH' | 'CHEQUE' | 'BANK_TRANSFER';
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

  hardDeleteEntry(entryId: number): Observable<void> {
    return this.http.delete<void>(`${this.collectBase}/${entryId}`);
  }

  getPendingEntriesForBill(billId: number): Observable<WorkerPaymentEntry[]> {
    return this.http.get<WorkerPaymentEntry[]>(`${this.collectBase}/pending-for-bill/${billId}`);
  }

  getCollectionNotesForBill(billId: number): Observable<CollectionNote[]> {
    return this.http.get<CollectionNote[]>(`${this.collectBase}/collection-notes-for-bill/${billId}`);
  }

  getBillsOverview(view: 'pending' | 'completed' = 'pending'): Observable<WorkerBillOverview[]> {
    return this.http.get<WorkerBillOverview[]>(`${this.base}/bills/overview`, { params: { view } });
  }

  toggleCollectionOnly(billId: number): Observable<void> {
    return this.http.patch<void>(`${environment.apiUrl}/bills/${billId}/toggle-collection-only`, {});
  }

  getReturnsOverview(): Observable<WorkerReturnOverview[]> {
    return this.http.get<WorkerReturnOverview[]>(`${this.base}/returns/overview`);
  }

  getRemindersOverview(): Observable<WorkerReminderOverview[]> {
    return this.http.get<WorkerReminderOverview[]>(`${this.base}/reminders/overview`);
  }
}
