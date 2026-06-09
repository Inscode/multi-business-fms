import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ProductStockBalance {
  productId: number;
  productName: string;
  unitPrice: number;
  active: boolean;
  availableQty: number;
  damageQty: number;
}

export interface StockInItemDetail {
  productId: number;
  productName: string;
  quantity: number;
}

export interface StockInRequest {
  id: number;
  referenceNumber?: string;
  stockDate: string;
  notes?: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  submittedByName: string;
  createdAt: string;
  approvedByName?: string;
  approvedAt?: string;
  rejectionReason?: string;
  items: StockInItemDetail[];
}

export interface CreateProductRequest {
  name: string;
  unitPrice: number;
}

export interface SubmitStockInRequest {
  referenceNumber?: string;
  stockDate: string;
  items: { productId: number; quantity: number }[];
  notes?: string;
}

@Injectable({ providedIn: 'root' })
export class InventoryService {
  private api = `${environment.apiUrl}/inventory`;

  constructor(private http: HttpClient) {}

  getBalances(): Observable<ProductStockBalance[]> {
    return this.http.get<ProductStockBalance[]>(`${this.api}/balances`);
  }

  createProduct(req: CreateProductRequest): Observable<any> {
    return this.http.post<any>(`${this.api}/products`, req);
  }

  updateProduct(id: number, req: CreateProductRequest): Observable<any> {
    return this.http.put<any>(`${this.api}/products/${id}`, req);
  }

  deleteProduct(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/products/${id}`);
  }

  setProductQty(id: number, qty: number): Observable<void> {
    return this.http.patch<void>(`${this.api}/products/${id}/set-qty`, { qty });
  }

  deactivateProduct(id: number): Observable<void> {
    return this.http.patch<void>(`${this.api}/products/${id}/deactivate`, {});
  }

  activateProduct(id: number): Observable<void> {
    return this.http.patch<void>(`${this.api}/products/${id}/activate`, {});
  }

  addOpeningStock(req: SubmitStockInRequest): Observable<void> {
    return this.http.post<void>(`${this.api}/opening-stock`, req);
  }

  submitStockIn(req: SubmitStockInRequest): Observable<StockInRequest> {
    return this.http.post<StockInRequest>(`${this.api}/stock-in`, req);
  }

  getAllStockIn(): Observable<StockInRequest[]> {
    return this.http.get<StockInRequest[]>(`${this.api}/stock-in`);
  }

  getPendingStockIn(): Observable<StockInRequest[]> {
    return this.http.get<StockInRequest[]>(`${this.api}/stock-in/pending`);
  }

  approveStockIn(id: number): Observable<void> {
    return this.http.patch<void>(`${this.api}/stock-in/${id}/approve`, {});
  }

  rejectStockIn(id: number, reason: string): Observable<void> {
    return this.http.patch<void>(`${this.api}/stock-in/${id}/reject`, { reason });
  }
}
