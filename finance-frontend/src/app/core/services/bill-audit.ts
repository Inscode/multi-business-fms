import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type BillAuditMarkType = 'IN_HAND' | 'PAID_NOT_ENTERED' | 'MISSING';

export interface BillAuditSession {
  id: number;
  periodMonth: string;
  businessScope: string | null;
  areaScope: string | null;
  openedById: number | null;
  openedByName: string | null;
  /** True when the signed-in user may change this sweep's marks. */
  canEdit: boolean;
  /** True only when they opened it — admins can edit others' without owning them. */
  mine: boolean;
  openedAt: string;
  closedAt: string | null;
  totalInScope: number;
  inHand: number;
  paidNotEntered: number;
  missing: number;
  unchecked: number;
}

export interface BillAuditRow {
  billId: number;
  billNumber: string;
  billDate: string;
  customerName: string;
  area: string | null;
  business: string | null;
  totalAmount: number;
  balanceRemaining: number;
  status: string;
  workerName: string | null;
  markType: BillAuditMarkType | null;
  note: string | null;
  markedByName: string | null;
  markedAt: string | null;
}

@Injectable({ providedIn: 'root' })
export class BillAuditService {
  private base = `${environment.apiUrl}/bill-audit`;

  constructor(private http: HttpClient) {}

  /**
   * Opens the month's sweep, or returns the one already in progress. One sweep covers
   * every business and area — narrowing is a view filter, never a different sweep.
   */
  openSession(month: string): Observable<BillAuditSession> {
    const params = new HttpParams().set('month', month);
    return this.http.post<BillAuditSession>(`${this.base}/sessions`, {}, { params });
  }

  listSessions(): Observable<BillAuditSession[]> {
    return this.http.get<BillAuditSession[]>(`${this.base}/sessions`);
  }

  getRows(sessionId: number): Observable<BillAuditRow[]> {
    return this.http.get<BillAuditRow[]>(`${this.base}/sessions/${sessionId}/rows`);
  }

  /** markType null clears the mark and puts the bill back on the working list. */
  mark(sessionId: number, billId: number, markType: BillAuditMarkType | null, note?: string): Observable<BillAuditRow> {
    return this.http.post<BillAuditRow>(`${this.base}/mark`, { sessionId, billId, markType, note });
  }

  closeSession(sessionId: number): Observable<BillAuditSession> {
    return this.http.patch<BillAuditSession>(`${this.base}/sessions/${sessionId}/close`, {});
  }
}
