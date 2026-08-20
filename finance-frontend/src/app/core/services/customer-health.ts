import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** GOOD, WATCH or CAREFUL. */
export type HealthRating = 'GOOD' | 'WATCH' | 'CAREFUL';

/**
 * How a customer has behaved about paying, for one business.
 *
 * Per business because a shop can be reliable on stationery and slow on Rainco, and one
 * blended figure hides the split worth knowing before extending credit again.
 */
export interface BusinessHealth {
  business: string;
  rating: HealthRating;
  /** Why the rating came out that way, in words. */
  reasons: string[];

  avgDaysToSettle?: number;
  avgDaysToSettleRecent?: number;
  worstDaysToSettle?: number;

  /**
   * Bills that ran past their own terms — counted per bill, not read off the average.
   *
   * Cash is due on delivery and credit runs to 70 days, so a customer buying both
   * averages the two together and lands between the lines without touching either:
   * punctual by arithmetic, late on half their bills in fact.
   */
  overTermsCount?: number;
  badlyLateCount?: number;
  overTermsPct?: number;
  /** How far past its own terms the worst open bill has run, in days. */
  worstOpenOverTerms?: number;
  settledBillCount: number;

  currentOutstanding: number;
  openBillCount: number;
  oldestOpenDays?: number;
  overdueAmount: number;

  bouncedChequeCount: number;
  lastBouncedChequeDate?: string;
  partialPaymentCount: number;

  damageReturnPct: number;
  damageReturnAmount: number;

  totalBilled: number;
  totalPaid: number;
  billCount: number;
  firstBillDate?: string;
  lastBillDate?: string;
  daysSinceLastBill?: number;
}

export interface CustomerHealth {
  customerId?: number;
  customerName: string;
  area?: string;
  phone?: string;
  tier?: string;
  businesses: BusinessHealth[];
  /** The worst rating across the businesses — for sorting and a single badge. */
  overallRating: HealthRating;
}

@Injectable({ providedIn: 'root' })
export class CustomerHealthService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/customers`;

  /** One customer's record, business by business. */
  getForCustomer(id: number): Observable<CustomerHealth> {
    return this.http.get<CustomerHealth>(`${this.apiUrl}/${id}/health`);
  }

  /** Every customer rated for one business, worst first. */
  getForBusiness(business: string): Observable<CustomerHealth[]> {
    return this.http.get<CustomerHealth[]>(`${this.apiUrl}/health`, {
      params: { business },
    });
  }
}
