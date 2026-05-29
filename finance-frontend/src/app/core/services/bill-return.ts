import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface BillReturnItemRequest {
  productId?: number;
  itemName?: string;
  unitPrice?: number;
  quantityRequested: number;
  quantityReturned?: number;
}

export interface CreateBillReturnRequest {
  returnType: string;
  items: BillReturnItemRequest[];
  discountPercentage?: number;
  discountFixed?: number;
  predictedValue?: number;
  responsibleWorkerId?: number;
  notes?: string;
}

export interface BillReturnItemResponse {
  id: number;
  productId?: number;
  itemName: string;
  unitPrice: number;
  quantityRequested: number;
  quantityReturned?: number;
  lineTotal: number;
}

export interface BillReturnResponse {
  id: number;
  billId: number;
  billNumber: string;
  customerName: string;
  business: string;
  returnType: string;
  status: string;
  items: BillReturnItemResponse[];
  itemsTotal: number;
  discountPercentage?: number;
  discountFixed?: number;
  calculatedReturnAmount: number;
  predictedValue?: number;
  approvedWith?: string;
  approvedAmount?: number;
  rejectionReason?: string;
  notes?: string;
  responsibleWorkerName?: string;
  submittedByName: string;
  submittedAt: string;
  reviewedByName?: string;
  reviewedAt?: string;
  shortfallAmount?: number;
}

export interface ReceivedItemDto {
  id: number;
  quantityReturned: number;
}

export interface ApproveReturnRequest {
  approveWith: string;
  items: ReceivedItemDto[];
}

@Injectable({ providedIn: 'root' })
export class BillReturnService {
  private apiUrl = `${environment.apiUrl}/bill-returns`;

  constructor(private http: HttpClient) {}

  create(billId: number, req: CreateBillReturnRequest): Observable<BillReturnResponse> {
    return this.http.post<BillReturnResponse>(`${this.apiUrl}/bills/${billId}`, req);
  }

  getAll(status?: string): Observable<BillReturnResponse[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);
    return this.http.get<BillReturnResponse[]>(this.apiUrl, { params });
  }

  getForBill(billId: number): Observable<BillReturnResponse[]> {
    return this.http.get<BillReturnResponse[]>(`${this.apiUrl}/bills/${billId}`);
  }

  getPendingCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.apiUrl}/pending-count`);
  }

  approve(id: number, req: ApproveReturnRequest): Observable<BillReturnResponse> {
    return this.http.patch<BillReturnResponse>(`${this.apiUrl}/${id}/approve`, req);
  }

  reject(id: number, reason: string): Observable<BillReturnResponse> {
    return this.http.patch<BillReturnResponse>(`${this.apiUrl}/${id}/reject`, { reason });
  }
}