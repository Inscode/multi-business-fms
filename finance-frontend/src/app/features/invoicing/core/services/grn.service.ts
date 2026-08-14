import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CategoryType } from '../models/models';
import { environment } from '../../../../../environments/environment';

export type GrnStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface GrnLine {
  id: number;
  itemId: number;
  itemCode: string;
  itemDescription: string;
  brandName: string | null;
  qty: number;
  unitCost: number | null;
  /** qty x unit cost, before discount */
  lineTotal: number | null;
  /** line total less the note's discount */
  netTotal: number | null;
}

export interface Grn {
  id: number;
  grnNo: string;
  category: CategoryType;
  supplierName: string | null;
  receivedDate: string;
  paymentTermsDays: number | null;
  dueDate: string | null;
  paymentRequired: boolean;
  status: GrnStatus;
  rejectionReason: string | null;
  notes: string | null;
  submittedBy: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  createdAt: string;
  discountPct: number;
  /** Gross: sum of qty x unit cost */
  totalCost: number | null;
  discountAmount: number | null;
  /** Final value payable, after discount */
  netTotal: number | null;
  totalQty: number | null;
  lines: GrnLine[];
}

export interface GrnRequest {
  category: CategoryType;
  supplierName?: string;
  receivedDate: string;
  paymentTermsDays?: number;
  paymentRequired?: boolean;
  notes?: string;
  // No unit cost — the server always prices from the catalog
  lines: { itemId: number; qty: number }[];
}

@Injectable({ providedIn: 'root' })
export class GrnService {
  private base = `${environment.apiUrl}/invoicing/grn`;

  constructor(private http: HttpClient) {}

  list(status?: GrnStatus): Observable<Grn[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);
    return this.http.get<Grn[]>(this.base, { params });
  }

  create(req: GrnRequest): Observable<Grn> {
    return this.http.post<Grn>(this.base, req);
  }

  approve(id: number): Observable<Grn> {
    return this.http.post<Grn>(`${this.base}/${id}/approve`, {});
  }

  /** Admin only, PENDING notes only. */
  updateLineQty(grnId: number, lineId: number, qty: number): Observable<Grn> {
    return this.http.patch<Grn>(`${this.base}/${grnId}/lines/${lineId}`, {}, {
      params: new HttpParams().set('qty', qty),
    });
  }

  /** Opening stock owes the principal nothing — keeps it out of the forecast. */
  setPaymentRequired(id: number, required: boolean): Observable<Grn> {
    return this.http.patch<Grn>(`${this.base}/${id}/payment-required`, {}, {
      params: new HttpParams().set('required', required),
    });
  }

  removeLine(grnId: number, lineId: number): Observable<Grn> {
    return this.http.delete<Grn>(`${this.base}/${grnId}/lines/${lineId}`);
  }

  reject(id: number, reason: string): Observable<Grn> {
    return this.http.post<Grn>(`${this.base}/${id}/reject`, {}, {
      params: new HttpParams().set('reason', reason),
    });
  }
}
