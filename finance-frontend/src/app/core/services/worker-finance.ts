import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type WorkerFinanceStatus = 'PENDING_OWNER' | 'OWNER_APPROVED' | 'PAID' | 'REJECTED';
export type AdvanceBonusType = 'ADVANCE' | 'BONUS';

export interface WorkerTabPurchaseItemRequest {
  description: string;
  quantity: number;
  unitPrice: number;
}

export interface WorkerTabPurchaseRequest {
  recipientId: number;
  billDate: string;
  notes?: string;
  items: WorkerTabPurchaseItemRequest[];
}

export interface WorkerTabPurchaseItemResponse {
  id: number;
  description: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface WorkerTabPurchaseResponse {
  id: number;
  recipientId: number;
  recipientName: string;
  billDate: string;
  month: string;
  totalAmount: number;
  status: WorkerFinanceStatus;
  notes?: string;
  items: WorkerTabPurchaseItemResponse[];
  enteredByName: string;
  ownerReviewedByName?: string;
  ownerReviewedAt?: string;
  rejectionReason?: string;
  adminConfirmedByName?: string;
  adminConfirmedAt?: string;
  createdAt: string;
}

export interface WorkerSalaryDeductionResponse {
  id: number;
  recipientId: number;
  recipientName: string;
  deductionMonth: string;
  amount: number;
  deductionType: string;
  referenceId: number;
  description?: string;
  createdByName: string;
  createdAt: string;
}

export interface WorkerAdvanceBonusRequest {
  recipientId: number;
  type: AdvanceBonusType;
  amount: number;
  reason: string;
  month: string;
  notes?: string;
}

export interface WorkerAdvanceBonusResponse {
  id: number;
  recipientId: number;
  recipientName: string;
  type: AdvanceBonusType;
  amount: number;
  reason: string;
  month: string;
  paymentDate?: string;
  status: WorkerFinanceStatus;
  recoveredAmount: number;
  fullyRecovered: boolean;
  notes?: string;
  enteredByName: string;
  ownerReviewedByName?: string;
  ownerReviewedAt?: string;
  rejectionReason?: string;
  adminConfirmedByName?: string;
  adminConfirmedAt?: string;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class WorkerFinanceService {
  private tabUrl = `${environment.apiUrl}/worker-finance/tab-purchases`;
  private abUrl = `${environment.apiUrl}/worker-finance/advance-bonus`;

  constructor(private http: HttpClient) {}

  // ── Tab Purchases ──────────────────────────────────────────────

  createTabPurchase(req: WorkerTabPurchaseRequest): Observable<WorkerTabPurchaseResponse> {
    return this.http.post<WorkerTabPurchaseResponse>(this.tabUrl, req);
  }

  getAllTabPurchases(): Observable<WorkerTabPurchaseResponse[]> {
    return this.http.get<WorkerTabPurchaseResponse[]>(this.tabUrl);
  }

  getTabPurchasesPendingOwner(): Observable<WorkerTabPurchaseResponse[]> {
    return this.http.get<WorkerTabPurchaseResponse[]>(`${this.tabUrl}/pending-owner`);
  }

  getTabPurchasesPendingAdmin(): Observable<WorkerTabPurchaseResponse[]> {
    return this.http.get<WorkerTabPurchaseResponse[]>(`${this.tabUrl}/pending-admin`);
  }

  getTabPurchasesForRecipient(recipientId: number): Observable<WorkerTabPurchaseResponse[]> {
    return this.http.get<WorkerTabPurchaseResponse[]>(`${this.tabUrl}/recipient/${recipientId}`);
  }

  getPaidTabPurchasesForMonth(recipientId: number, month: string): Observable<WorkerTabPurchaseResponse[]> {
    const params = new HttpParams().set('recipientId', recipientId).set('month', month);
    return this.http.get<WorkerTabPurchaseResponse[]>(`${this.tabUrl}/paid`, { params });
  }

  tabOwnerApprove(id: number): Observable<WorkerTabPurchaseResponse> {
    return this.http.patch<WorkerTabPurchaseResponse>(`${this.tabUrl}/${id}/owner-approve`, {});
  }

  tabOwnerReject(id: number, reason: string): Observable<WorkerTabPurchaseResponse> {
    return this.http.patch<WorkerTabPurchaseResponse>(`${this.tabUrl}/${id}/owner-reject`, { reason });
  }

  tabAdminConfirm(id: number): Observable<WorkerTabPurchaseResponse> {
    return this.http.patch<WorkerTabPurchaseResponse>(`${this.tabUrl}/${id}/admin-confirm`, {});
  }

  getTabCounts(): Observable<{ pendingOwner: number; pendingAdmin: number }> {
    return this.http.get<{ pendingOwner: number; pendingAdmin: number }>(`${this.tabUrl}/counts`);
  }

  getTabPurchasesByDateRange(from: string, to: string): Observable<WorkerTabPurchaseResponse[]> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<WorkerTabPurchaseResponse[]>(`${this.tabUrl}/by-date-range`, { params });
  }

  // ── Advance / Bonus ───────────────────────────────────────────

  createAdvanceBonus(req: WorkerAdvanceBonusRequest): Observable<WorkerAdvanceBonusResponse> {
    return this.http.post<WorkerAdvanceBonusResponse>(this.abUrl, req);
  }

  getAllAdvanceBonus(): Observable<WorkerAdvanceBonusResponse[]> {
    return this.http.get<WorkerAdvanceBonusResponse[]>(this.abUrl);
  }

  getAdvanceBonusPendingOwner(): Observable<WorkerAdvanceBonusResponse[]> {
    return this.http.get<WorkerAdvanceBonusResponse[]>(`${this.abUrl}/pending-owner`);
  }

  getAdvanceBonusPendingAdmin(): Observable<WorkerAdvanceBonusResponse[]> {
    return this.http.get<WorkerAdvanceBonusResponse[]>(`${this.abUrl}/pending-admin`);
  }

  getAdvanceBonusForRecipient(recipientId: number): Observable<WorkerAdvanceBonusResponse[]> {
    return this.http.get<WorkerAdvanceBonusResponse[]>(`${this.abUrl}/recipient/${recipientId}`);
  }

  getOutstandingAdvances(recipientId: number): Observable<WorkerAdvanceBonusResponse[]> {
    return this.http.get<WorkerAdvanceBonusResponse[]>(`${this.abUrl}/recipient/${recipientId}/outstanding-advances`);
  }

  abOwnerApprove(id: number): Observable<WorkerAdvanceBonusResponse> {
    return this.http.patch<WorkerAdvanceBonusResponse>(`${this.abUrl}/${id}/owner-approve`, {});
  }

  abOwnerReject(id: number, reason: string): Observable<WorkerAdvanceBonusResponse> {
    return this.http.patch<WorkerAdvanceBonusResponse>(`${this.abUrl}/${id}/owner-reject`, { reason });
  }

  abAdminConfirm(id: number): Observable<WorkerAdvanceBonusResponse> {
    return this.http.patch<WorkerAdvanceBonusResponse>(`${this.abUrl}/${id}/admin-confirm`, {});
  }

  recoverAdvance(id: number, amount: number, recoveryMonth?: string): Observable<WorkerAdvanceBonusResponse> {
    return this.http.patch<WorkerAdvanceBonusResponse>(`${this.abUrl}/${id}/recover`, { amount, recoveryMonth });
  }

  getSalaryDeductions(recipientId: number, month: string): Observable<WorkerSalaryDeductionResponse[]> {
    const params = new HttpParams().set('recipientId', recipientId).set('month', month);
    return this.http.get<WorkerSalaryDeductionResponse[]>(`${this.tabUrl}/salary-deductions`, { params });
  }

  getAdvanceBonusCounts(): Observable<{ pendingOwner: number; pendingAdmin: number }> {
    return this.http.get<{ pendingOwner: number; pendingAdmin: number }>(`${this.abUrl}/counts`);
  }

  getAdvanceBonusByDateRange(from: string, to: string): Observable<WorkerAdvanceBonusResponse[]> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<WorkerAdvanceBonusResponse[]>(`${this.abUrl}/by-date-range`, { params });
  }
}
