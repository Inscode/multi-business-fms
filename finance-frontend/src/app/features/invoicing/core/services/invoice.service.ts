import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Invoice, InvoicePrint, InvoiceSummary, Page } from '../models/models';
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

  create(req: any): Observable<Invoice> {
    return this.http.post<Invoice>(this.base, req);
  }

  print(id: number): Observable<InvoicePrint> {
    return this.http.post<InvoicePrint>(`${this.base}/${id}/print`, {});
  }
}
