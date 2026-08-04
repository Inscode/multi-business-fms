import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Item } from '../models/models';
import { environment } from '../../../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class ItemService {
  private base = `${environment.apiUrl}/invoicing/items`;
  constructor(private http: HttpClient) {}

  list(category?: string): Observable<Item[]> {
    let params = new HttpParams();
    if (category) params = params.set('category', category);
    return this.http.get<Item[]>(this.base, { params });
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
