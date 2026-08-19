import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface StockTakeLine {
  itemId?: number;
  itemCode?: string;
  countedQty?: number;
  /** Null leaves the damage bucket alone — not the same as counting zero. */
  countedDamageQty?: number;
}

export interface StockTakeRequest {
  reference: string;
  countedOn?: string;
  lines: StockTakeLine[];
  /** Whether items absent from the sheet should be set to zero. Off by default. */
  zeroUncounted?: boolean;
}

export type StockTakeStatus =
  | 'MATCHED' | 'INCREASE' | 'DECREASE' | 'NOT_FOUND' | 'DUPLICATE';

export interface StockTakeRow {
  itemId?: number;
  itemCode: string;
  description?: string;
  brand?: string;
  category?: string;
  systemQty?: number;
  countedQty?: number;
  delta?: number;
  systemDamageQty?: number;
  countedDamageQty?: number;
  damageDelta?: number;
  status: StockTakeStatus;
  warning?: string;
}

/**
 * What a count would do, worked out before anything is written.
 *
 * The counted figure overwrites the system's, so there is no balance afterwards to
 * reveal a typo — the preview is the only place a wrong number is still visible.
 */
export interface StockTakePreview {
  reference: string;
  lineCount: number;
  changedCount: number;
  matchedCount: number;
  notFoundCount: number;
  netUnitChange: number;
  rows: StockTakeRow[];
  /** Items the sheet never mentioned, with what the system still holds. */
  uncounted: StockTakeRow[];
}

@Injectable({ providedIn: 'root' })
export class StockTakeService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/invoicing/items/stock-take`;

  preview(req: StockTakeRequest): Observable<StockTakePreview> {
    return this.http.post<StockTakePreview>(`${this.apiUrl}/preview`, req);
  }

  apply(req: StockTakeRequest): Observable<StockTakePreview> {
    return this.http.post<StockTakePreview>(`${this.apiUrl}/apply`, req);
  }
}
