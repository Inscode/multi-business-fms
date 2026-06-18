import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface BackorderItemResp {
  id: number;
  productId: number;
  productName: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  amountToAdd: number;
  availableQty: number;
}

export interface BackorderRequest {
  id: number;
  billId: number;
  billNumber: string;
  customerName: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  submittedByName: string | null;
  reviewedByName: string | null;
  submittedAt: string;
  reviewedAt: string | null;
  notes: string | null;
  rejectionReason: string | null;
  items: BackorderItemResp[];
  totalAmountToAdd: number;
  hasInsufficientStock: boolean;
}

export interface BackorderBillOption {
  id: number;
  billNumber: string;
  customerName: string;
  totalAmount: number;
  balanceRemaining: number;
  billDate: string;
  status: string;
}

@Injectable({ providedIn: 'root' })
export class BackorderService {
  private apiUrl = `${environment.apiUrl}/backorders`;

  constructor(private http: HttpClient) {}

  getAll(): Observable<BackorderRequest[]> {
    return this.http.get<BackorderRequest[]>(this.apiUrl);
  }

  getActiveBills(): Observable<BackorderBillOption[]> {
    return this.http.get<BackorderBillOption[]>(`${this.apiUrl}/bills`);
  }

  submit(payload: {
    billId: number;
    items: { productId: number; quantity: number; amountToAdd: number }[];
    notes?: string;
  }): Observable<BackorderRequest> {
    return this.http.post<BackorderRequest>(this.apiUrl, payload);
  }

  approve(id: number): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${id}/approve`, {});
  }

  reject(id: number, reason: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${id}/reject`, { reason });
  }
}
