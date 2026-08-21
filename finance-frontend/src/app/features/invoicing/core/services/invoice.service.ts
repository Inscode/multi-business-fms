import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Invoice, InvoicePrint, InvoiceSummary, Page, Quote } from '../models/models';
import { environment } from '../../../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class InvoiceService {
  private base = `${environment.apiUrl}/invoicing/invoices`;
  constructor(private http: HttpClient) {}

  getById(id: number): Observable<Invoice> {
    return this.http.get<Invoice>(`${this.base}/${id}`);
  }

  search(filters: { method?: string; from?: string; to?: string; search?: string; page?: number; size?: number }): Observable<Page<InvoiceSummary>> {
    let params = new HttpParams();
    if (filters.method) params = params.set('method', filters.method);
    if (filters.from)   params = params.set('from', filters.from);
    if (filters.to)     params = params.set('to', filters.to);
    if (filters.search) params = params.set('search', filters.search);
    params = params.set('page', filters.page ?? 0).set('size', filters.size ?? 20);
    return this.http.get<Page<InvoiceSummary>>(this.base, { params });
  }

  update(id: number, req: any): Observable<Invoice> {
    return this.http.put<Invoice>(`${this.base}/${id}`, req);
  }

  /** Prices a draft without saving, so the discount shows while the invoice is built. */
  quote(req: any): Observable<Quote> {
    return this.http.post<Quote>(`${this.base}/quote`, req);
  }

  create(req: any): Observable<Invoice> {
    return this.http.post<Invoice>(this.base, req);
  }

  print(id: number): Observable<InvoicePrint> {
    return this.http.post<InvoicePrint>(`${this.base}/${id}/print`, {});
  }

  /**
   * Voids an invoice and the bill it raised, putting the stock back.
   *
   * <p>The invoice is kept under its number. Cancelling the bill goes through the same
   * path the bills section uses, so a voided invoice and a voided bill read the same way.
   */
  cancel(id: number, reason: string) {
    return this.http.patch<void>(`${this.base}/${id}/cancel`, { reason });
  }

  /**
   * Removes an invoice from this section only.
   *
   * <p>The bill is left where it is — it belongs to the bills section, may predate this
   * invoice, and may already be collecting money. Stock still comes back, since this
   * invoice is what took it.
   */
  delete(id: number) {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
