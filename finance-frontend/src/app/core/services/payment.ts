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
  area: string | null;
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
  collectionNoteId: number | null;
  collectedByOwnerName: string | null;
  collectedByOwnerAt: string | null;
  collectedByWorkerId: number | null;
  collectedByWorkerName: string | null;
  collectorNote: string | null;
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

export interface BulkPaymentBillItem {
  billId: number;
  amount: number;
}

export interface BulkPaymentRequest {
  bills: BulkPaymentBillItem[];
  paymentType: string;
  chequeNumber?: string;
  bankName?: string;
  branchName?: string;
  chequeDate?: string;
  referenceNumber?: string;
  paymentDate?: string;
  notes?: string;
}

export interface PaymentGroupResponse {
  id: number;
  paymentType: string;
  chequeNumber?: string;
  bankName?: string;
  branchName?: string;
  referenceNumber?: string;
  chequeDate?: string;
  paymentDate: string;
  totalAmount: number;
  notes?: string;
  status: string;
  enteredByName: string;
  confirmedByName?: string;
  confirmedAt?: string;
  returnReason?: string;
  createdAt: string;
  payments: PaymentResponse[];
}

@Injectable({
  providedIn: 'root',
})
export class Payment {
  private apiUrl = `${environment.apiUrl}/payments`;

  constructor(private http: HttpClient) {}

  getAllPayments(status?: string, from?: string, to?: string): Observable<PaymentResponse[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);
    if (from)   params = params.set('from', from);
    if (to)     params = params.set('to', to);
    return this.http.get<PaymentResponse[]>(this.apiUrl, { params });
  }

  enterPayment(billId: number, request: PaymentRequest): Observable<PaymentResponse> {
    return this.http.post<PaymentResponse>(`${this.apiUrl}/bills/${billId}`, request);
  }

  updatePayment(id: number, request: PaymentRequest): Observable<PaymentResponse> {
    return this.http.put<PaymentResponse>(`${this.apiUrl}/${id}`, request);
  }

  confirmPayment(id: number): Observable<PaymentResponse> {
    return this.http.patch<PaymentResponse>(`${this.apiUrl}/${id}/confirm`, {});
  }

  rejectPayment(id: number, reason: string): Observable<PaymentResponse> {
    return this.http.patch<PaymentResponse>(`${this.apiUrl}/${id}/reject`, { reason });
  }

  markChequeReturned(id: number, returnReason: string): Observable<PaymentResponse> {
    return this.http.patch<PaymentResponse>(`${this.apiUrl}/${id}/return`, { returnReason });
  }

  getPaymentsByBill(billId: number): Observable<PaymentResponse[]> {
    return this.http.get<PaymentResponse[]>(`${this.apiUrl}/bill/${billId}`);
  }

  getMyEnteredPayments(): Observable<PaymentResponse[]> {
    return this.http.get<PaymentResponse[]>(`${this.apiUrl}/my-entered`);
  }

  enterBulkPayment(request: BulkPaymentRequest): Observable<PaymentGroupResponse> {
    return this.http.post<PaymentGroupResponse>(`${this.apiUrl}/bulk`, request);
  }

  getGroups(status?: string): Observable<PaymentGroupResponse[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);
    return this.http.get<PaymentGroupResponse[]>(`${this.apiUrl}/groups`, { params });
  }

  getGroup(id: number): Observable<PaymentGroupResponse> {
    return this.http.get<PaymentGroupResponse>(`${this.apiUrl}/groups/${id}`);
  }

  confirmGroup(id: number): Observable<PaymentGroupResponse> {
    return this.http.patch<PaymentGroupResponse>(`${this.apiUrl}/groups/${id}/confirm`, {});
  }

  returnGroup(id: number, returnReason: string): Observable<PaymentGroupResponse> {
    return this.http.patch<PaymentGroupResponse>(`${this.apiUrl}/groups/${id}/return`, { returnReason });
  }

  deletePayment(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  getFutureCheques(customer?: string): Observable<PaymentResponse[]> {
    let params = new HttpParams();
    if (customer?.trim()) params = params.set('customer', customer.trim());
    return this.http.get<PaymentResponse[]>(`${this.apiUrl}/future-cheques`, { params });
  }

  searchByChequeNumber(chequeNumber: string): Observable<PaymentResponse[]> {
    return this.http.get<PaymentResponse[]>(`${this.apiUrl}/cheque-search`, {
      params: { chequeNumber },
    });
  }
}