import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export interface ReviewInvoice {
  id: number;
  invoiceNo: string;
  externalRef?: string | null;
  method: string;
  invoiceDate: string;
  customerName: string;
  invoiceType: 'CASH' | 'CREDIT';
  grossTotal: number;
  totalDiscount: number;
  netTotal: number;

  /** Name on the source invoice, when it isn't the customer's own. */
  billedName?: string | null;
  originalCustomerName?: string | null;
  customerChanged: boolean;
  customerChangedBy?: string | null;
  source: 'MANUAL' | 'IMPORT';
  createdBy?: string | null;
  createdAt: string;
  /** Attached to a bill that already existed — this invoice only moved the stock. */
  billLinkedExisting?: boolean;
  /** Set when a line carries a typed free quantity. */
  freeIssueAddedBy?: string | null;
  freeIssueAddedAt?: string | null;
  editedBy?: string | null;
  editedAt?: string | null;
  reviewed: boolean;
  reviewedBy?: string | null;
  reviewedAt?: string | null;
}

export interface ReviewPage {
  content: ReviewInvoice[];
  totalElements: number;
  number: number;
  size: number;
}

export interface ReviewFilters {
  reviewed?: boolean | null;
  source?: 'MANUAL' | 'IMPORT' | null;
  changedOnly?: boolean;
  from?: string | null;
  to?: string | null;
  search?: string | null;
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class InvoiceReviewService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/invoicing/invoices`;

  list(f: ReviewFilters): Observable<ReviewPage> {
    let p = new HttpParams()
      .set('page', f.page ?? 0)
      .set('size', f.size ?? 25);
    if (f.reviewed != null)  p = p.set('reviewed', f.reviewed);
    if (f.source)            p = p.set('source', f.source);
    if (f.changedOnly)       p = p.set('changedOnly', true);
    if (f.from)              p = p.set('from', f.from);
    if (f.to)                p = p.set('to', f.to);
    if (f.search?.trim())    p = p.set('search', f.search.trim());
    return this.http.get<ReviewPage>(`${this.base}/review`, { params: p });
  }

  pendingCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.base}/review/count`);
  }

  setReviewed(id: number, reviewed: boolean): Observable<ReviewInvoice> {
    return this.http.patch<ReviewInvoice>(`${this.base}/${id}/review`, {}, {
      params: new HttpParams().set('reviewed', reviewed),
    });
  }

  setReviewedBulk(ids: number[], reviewed: boolean): Observable<{ updated: number }> {
    return this.http.patch<{ updated: number }>(`${this.base}/review/bulk`, ids, {
      params: new HttpParams().set('reviewed', reviewed),
    });
  }
}
