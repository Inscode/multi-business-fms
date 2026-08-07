import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface DamageStockItem {
  productId: number;
  productName: string;
  unitPrice: number;
  damageQty: number;
}

export interface DamageDispatchItemResponse {
  id: number;
  productId: number;
  productName: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
}

export interface DamageDispatchResponse {
  id: number;
  business: string;
  dispatchDate: string;
  totalValue: number;
  predictedValue: number | null;
  notes: string | null;
  enteredByName: string | null;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  rejectionReason: string | null;
  reviewedByName: string | null;
  reviewedAt: string | null;
  createdAt: string;
  items: DamageDispatchItemResponse[];
}

export interface CreateDamageDispatchRequest {
  business: string;
  dispatchDate: string;
  notes?: string;
  predictedValue?: number;
  items: { productId: number; quantity: number }[];
}

@Injectable({ providedIn: 'root' })
export class DamageDispatchService {
  private api = `${environment.apiUrl}/damage-dispatches`;

  constructor(private http: HttpClient) {}

  getDamageStock(business: string): Observable<DamageStockItem[]> {
    return this.http.get<DamageStockItem[]>(`${this.api}/damage-stock`, { params: { business } });
  }

  create(req: CreateDamageDispatchRequest): Observable<DamageDispatchResponse> {
    return this.http.post<DamageDispatchResponse>(this.api, req);
  }

  getAll(): Observable<DamageDispatchResponse[]> {
    return this.http.get<DamageDispatchResponse[]>(this.api);
  }

  /** Admin only — this is what actually deducts the damage stock. */
  approve(id: number): Observable<DamageDispatchResponse> {
    return this.http.patch<DamageDispatchResponse>(`${this.api}/${id}/approve`, {});
  }

  reject(id: number, reason: string): Observable<DamageDispatchResponse> {
    return this.http.patch<DamageDispatchResponse>(`${this.api}/${id}/reject`, {}, { params: { reason } });
  }

  getById(id: number): Observable<DamageDispatchResponse> {
    return this.http.get<DamageDispatchResponse>(`${this.api}/${id}`);
  }
}
