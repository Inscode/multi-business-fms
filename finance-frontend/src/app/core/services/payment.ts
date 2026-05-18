import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';


export interface PaymentResponse {
  id: number;
  billId: number;
  billNumber: string;
  customerName: string;
  business: string;
  billTotal: number;
  amountPaid: number;
  balanceRemaining: number;
  fullyPaid: boolean;
  paymentAmount: number;
  paymentType: string;
  status: string;
  isPartial: boolean;
  chequeNumber: string | null;
  chequeDate: string | null;
  bankName: string | null;
  branchName: string | null;
  referenceNumber: string | null;
  returnReason: string | null;
  enteredByName: string;
  confirmedByName: string | null;
  confirmedAt: string | null;
  paymentDate: string;
  notes: string | null;
  createdAt: string;
}

export interface PaymentRequest {
  amount: number;
  paymentType: string;
  paymentDate?: string;
  chequeNumber?: string;
  chequeDate?: string;
  bankName?: string;
  branchName?: string;
  referenceNumber?: string;
  notes?: string;
}

@Injectable({
  providedIn: 'root',
})
export class Payment {

  private apiUrl = `${environment.apiUrl}/payments`;

  constructor(private http: HttpClient) {}

  getAllPayments(status?: string): Observable<PaymentResponse[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);
    return this.http.get<PaymentResponse[]>(this.apiUrl, {params});
  }

  enterPayment(billId: number, request: PaymentRequest): Observable<PaymentResponse> {
    return this.http.post<PaymentResponse>(
      `${this.apiUrl}/bills/${billId}`, request);
  }

  updatePayment(id: number, request: PaymentRequest): Observable<PaymentResponse> {
    return this.http.put<PaymentResponse>(`${this.apiUrl}/${id}`, request);
  }

  confirmPayment(id: number): Observable<PaymentResponse> {
    return this.http.patch<PaymentResponse>(`${this.apiUrl}/${id}/confirm`, {});
  }

  markChequeReturned(id: number, returnReason: string): Observable<PaymentResponse> {
    return this.http.patch<PaymentResponse>(
      `${this.apiUrl}/${id}/return`, { returnReason });
  }

  getPaymentsByBill(billId: number): Observable<PaymentResponse[]> {
    return this.http.get<PaymentResponse[]>(`${this.apiUrl}/bill/${billId}`);
  }
}
