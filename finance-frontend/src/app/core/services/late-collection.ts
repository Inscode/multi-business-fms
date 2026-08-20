import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** Which side of the three credit lines a collection landed on. */
export type CollectionBand = 'ON_TIME' | 'WATCH' | 'LATE' | 'BEYOND_TERMS';

export interface CollectionBandRow {
  band: CollectionBand;
  label: string;
  amount: number;
  count: number;
  pct: number;
}

export interface LateCollectionCustomer {
  customerId?: number;
  customerName: string;
  area?: string;
  collected: number;
  /** The window between the danger line and the supplier deadline — disjoint from below. */
  lateAmount: number;
  /** Past the supplier deadline: the money that actually cost something. */
  beyondTermsAmount: number;
  /** Both together. */
  pastDangerAmount: number;
  avgDaysWeighted?: number;
  worstDays?: number;
  paymentCount: number;
}

export interface LateCollectionPayment {
  billId: number;
  billNumber: string;
  customerName: string;
  area?: string;
  billDate: string;
  paymentDate: string;
  days: number;
  band: CollectionBand;
  amount: number;
  paymentType?: string;
  /** CASH or CREDIT — the terms these days were judged against. */
  billType?: string;
}

/**
 * How long the money took to arrive, for everything collected in a period.
 *
 * Keyed on payment date, so a period reconciles against what actually reached the bank.
 */
export interface LateCollectionReport {
  business: string;
  from: string;
  to: string;

  /** The three lines, sent by the server so the page never hard-codes them. */
  sealDays: number;
  dangerDays: number;
  supplierDays: number;
  /** Cash is due on delivery, so it is judged on a much shorter run. */
  cashSealDays: number;
  cashDangerDays: number;
  cashSupplierDays: number;

  totalCollected: number;
  paymentCount: number;
  /** Weighted by amount — a large invoice paid at 90 days costs far more than a small one. */
  avgDaysWeighted?: number;

  beyondTermsAmount: number;
  beyondTermsPct: number;
  pastDangerAmount: number;
  pastDangerPct: number;

  bands: CollectionBandRow[];
  customers: LateCollectionCustomer[];
  payments: LateCollectionPayment[];
}

@Injectable({ providedIn: 'root' })
export class LateCollectionService {
  private http = inject(HttpClient);

  get(business: string | null, from: string, to: string): Observable<LateCollectionReport> {
    const params: Record<string, string> = { from, to };
    if (business) params['business'] = business;
    return this.http.get<LateCollectionReport>(
      `${environment.apiUrl}/payments/late-collections`, { params });
  }
}
