import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';


export interface BillFilter {
  business?: string;
  status?: string;
  excludeCompleted?: boolean;
}

export interface BillResponse {
  id: number;
  billNumber: string;
  business: string;
  division: string;
  billType: string;
  billSource: string;
  customerName: string;
  totalAmount: number;
  amountPaid: number;
  balanceRemaining: number;
  fullyPaid: boolean;
  status: string;
  workerId: number | null;
  workerName: string | null;
  enteredByName: string;
  receivedByName: string | null;
  receivedAt: string | null;
  area: string | null;
  billDate: string;
  notes: string | null;
  createdAt: string;
}

@Injectable({
  providedIn: 'root',
})
export class Bill {
  private apiUrl = `${environment.apiUrl}/bills`;

  constructor(private http: HttpClient) {}

  getBills(filter?: BillFilter): Observable<BillResponse[]> {
    let params = new HttpParams();
    if (filter?.business) params = params.set('business', filter.business);
    if (filter?.status)   params = params.set('status', filter.status);
    if (filter?.excludeCompleted !== undefined) {
      params = params.set('excludeCompleted', String(filter.excludeCompleted));
    }
    return this.http.get<BillResponse[]>(this.apiUrl, { params });
  }

  getBillById(id: number): Observable<BillResponse> {
    return this.http.get<BillResponse>(`${this.apiUrl}/${id}`);
  }

  createBill(payload: any): Observable<BillResponse> {
    return this.http.post<BillResponse>(this.apiUrl, payload);
  }

  assignBill(id: number, workerId: number): Observable<BillResponse> {
    return this.http.patch<BillResponse>(`${this.apiUrl}/${id}/assign`, { workerId });
  }

  markReceived(id: number): Observable<BillResponse> {
    return this.http.patch<BillResponse>(`${this.apiUrl}/${id}/receive`, {});
  }

  updateBill(id: number, payload: any): Observable<BillResponse> {
    return this.http.put<BillResponse>(`${this.apiUrl}/${id}`, payload);
  }

  markShopReceived(id: number): Observable<BillResponse> {
    return this.http.patch<BillResponse>(`${this.apiUrl}/${id}/shop-receive`, {});
  }

  markCompleted(id: number): Observable<BillResponse> {
    return this.http.patch<BillResponse>(`${this.apiUrl}/${id}/complete`, {});
  }

  cancelBill(id: number): Observable<BillResponse> {
    return this.http.patch<BillResponse>(`${this.apiUrl}/${id}/cancel`, {});
  }

  deleteBill(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}