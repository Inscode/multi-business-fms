import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** One of the recurring lorry rounds — Bandarawela, Badulla, Haputale. */
export interface RouteArea {
  id?: number;
  name: string;
  active?: boolean;
  sortOrder?: number;
}

export type DeliveryMode = 'UNSPECIFIED' | 'ROUTE' | 'IMMEDIATE' | 'STORE_PICKUP';
export type RunStatus = 'OPEN' | 'DISPATCHED' | 'COMPLETED' | 'CANCELLED';

/** A lorry round, with what it is carrying. */
export interface DeliveryRun {
  id: number;
  routeAreaIds: number[];
  /** "Bandarawela + Haputale" — the whole trip in one line. */
  areaName: string;
  areaNames: string[];
  plannedDate: string;
  /** The month it counts against, which is not always the month it went out. */
  runMonth?: string;
  status: RunStatus;
  notes?: string;
  openedBy?: string;
  openedAt?: string;
  closedBy?: string;
  closedAt?: string;

  /** What the admin checks the lorry against. */
  billCount: number;
  customerCount: number;
  totalValue: number;

  /** Detail view only. */
  bills?: any[];
}

/** One business's month. */
export interface MonthBusinessSummary {
  business: string;
  billCount: number;
  sales: number;
  paid: number;
  pending: number;
}

@Injectable({ providedIn: 'root' })
export class DeliveryService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/delivery`;

  areas(includeInactive = false): Observable<RouteArea[]> {
    return this.http.get<RouteArea[]>(`${this.base}/areas`,
      { params: new HttpParams().set('includeInactive', includeInactive) });
  }

  saveArea(area: RouteArea): Observable<RouteArea> {
    return this.http.post<RouteArea>(`${this.base}/areas`, area);
  }

  /** @param routeAreaIds one or more rounds — a lorry often does two or three together */
  /** @param runMonth first of the month it counts against; defaults to the date's month */
  open(routeAreaIds: number[], plannedDate: string, runMonth?: string,
       notes?: string): Observable<DeliveryRun> {
    return this.http.post<DeliveryRun>(`${this.base}/runs`,
      { routeAreaIds, plannedDate, runMonth, notes });
  }

  /**
   * The run this user is entering bills into, or null.
   *
   * <p>Read once when the bill form opens: the answer to "which round?" is given once
   * and then reused, rather than asked on every one of fifteen bills.
   */
  current(): Observable<DeliveryRun | null> {
    return this.http.get<DeliveryRun | null>(`${this.base}/runs/current`);
  }

  list(from?: string, to?: string, month?: string): Observable<DeliveryRun[]> {
    let params = new HttpParams();
    if (from)  params = params.set('from', from);
    if (to)    params = params.set('to', to);
    if (month) params = params.set('month', month);
    return this.http.get<DeliveryRun[]>(`${this.base}/runs`, { params });
  }

  /** A month by business: billed, collected, still out. Admin and owner only. */
  monthSummary(month?: string, mode?: string): Observable<MonthBusinessSummary[]> {
    let params = new HttpParams();
    if (month) params = params.set('month', month);
    if (mode)  params = params.set('mode', mode);
    return this.http.get<MonthBusinessSummary[]>(`${this.base}/summary`, { params });
  }

  detail(id: number): Observable<DeliveryRun> {
    return this.http.get<DeliveryRun>(`${this.base}/runs/${id}`);
  }

  setStatus(id: number, status: RunStatus): Observable<DeliveryRun> {
    return this.http.patch<DeliveryRun>(`${this.base}/runs/${id}/status`, { status });
  }

  /** Bills that could still join this round — on no run, and dated near it. */
  candidates(runId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/runs/${runId}/candidates`);
  }

  /** For bills entered before the round was decided, or one on the wrong round. */
  assignBills(runId: number, billIds: number[]): Observable<{ assigned: number }> {
    return this.http.post<{ assigned: number }>(`${this.base}/runs/${runId}/bills`, { billIds });
  }

  removeBill(billId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/runs/bills/${billId}`);
  }
}
