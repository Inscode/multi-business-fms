import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface SalaryRecipientResponse {
  id: number;
  name: string;
  designation: string;
  monthlySalary?: number;
  active: boolean;
}

export interface SalaryRecipientRequest {
  name: string;
  designation: string;
  monthlySalary: number;
  linkedWorkerId?: number;
  linkedUserId?: number;
}

export interface SalaryPaymentRequest {
  recipientId: number;
  month: string;
  amount: number;
  paymentDate: string;
  notes?: string;
}

export interface SalaryPaymentResponse {
  id: number;
  recipientId: number;
  recipientName: string;
  designation: string;
  month: string;
  amount: number;
  paymentDate: string;
  notes?: string;
  status: string;
  overSalary?: boolean;
  monthlySalary?: number;
  alreadyPaid?: number;
  remainingAfter?: number;
  enteredByName: string;
  reviewedByName?: string;
  reviewedAt?: string;
  rejectionReason?: string;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class SalaryService {
  private apiUrl = `${environment.apiUrl}/salary`;

  constructor(private http: HttpClient) {}

  getActiveRecipients(): Observable<SalaryRecipientResponse[]> {
    return this.http.get<SalaryRecipientResponse[]>(`${this.apiUrl}/recipients`);
  }

  getAllRecipients(): Observable<SalaryRecipientResponse[]> {
    return this.http.get<SalaryRecipientResponse[]>(`${this.apiUrl}/recipients/all`);
  }

  createRecipient(req: SalaryRecipientRequest): Observable<SalaryRecipientResponse> {
    return this.http.post<SalaryRecipientResponse>(`${this.apiUrl}/recipients`, req);
  }

  updateRecipient(id: number, req: SalaryRecipientRequest): Observable<SalaryRecipientResponse> {
    return this.http.put<SalaryRecipientResponse>(`${this.apiUrl}/recipients/${id}`, req);
  }

  deactivate(id: number): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/recipients/${id}/deactivate`, {});
  }

  activate(id: number): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/recipients/${id}/activate`, {});
  }

  createPayment(req: SalaryPaymentRequest): Observable<SalaryPaymentResponse> {
    return this.http.post<SalaryPaymentResponse>(`${this.apiUrl}/payments`, req);
  }

  getAllPayments(month?: string): Observable<SalaryPaymentResponse[]> {
    let params = new HttpParams();
    if (month) params = params.set('month', month);
    return this.http.get<SalaryPaymentResponse[]>(`${this.apiUrl}/payments`, { params });
  }

  getMyPayments(): Observable<SalaryPaymentResponse[]> {
    return this.http.get<SalaryPaymentResponse[]>(`${this.apiUrl}/payments/mine`);
  }

  approve(id: number): Observable<SalaryPaymentResponse> {
    return this.http.patch<SalaryPaymentResponse>(`${this.apiUrl}/payments/${id}/approve`, {});
  }

  reject(id: number, reason: string): Observable<SalaryPaymentResponse> {
    return this.http.patch<SalaryPaymentResponse>(`${this.apiUrl}/payments/${id}/reject`, { reason });
  }

  getPendingCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.apiUrl}/payments/pending-count`);
  }
}