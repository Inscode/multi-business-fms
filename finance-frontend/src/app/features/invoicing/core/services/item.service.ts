import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Item } from '../models/models';
import { environment } from '../../../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class ItemService {
  private base = `${environment.apiUrl}/invoicing/items`;
  constructor(private http: HttpClient) {}

  /** Active items only unless includeInactive — pickers must not offer retired items. */
  list(category?: string, includeInactive = false): Observable<Item[]> {
    let params = new HttpParams();
    if (category) params = params.set('category', category);
    if (includeInactive) params = params.set('includeInactive', 'true');
    return this.http.get<Item[]>(this.base, { params });
  }

  toggleActive(id: number): Observable<Item> {
    return this.http.patch<Item>(`${this.base}/${id}/toggle-active`, {});
  }

  create(req: any): Observable<Item> {
    return this.http.post<Item>(this.base, req);
  }

  update(id: number, req: any): Observable<Item> {
    return this.http.put<Item>(`${this.base}/${id}`, req);
  }

  adjustStock(req: { itemId: number; delta: number; notes?: string }): Observable<Item> {
    return this.http.post<Item>(`${this.base}/stock-adjust`, req);
  }
}
