import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface BillReminderResponse {
  id: number;
  billId: number;
  billNumber: string;
  customerName: string;
  area: string | null;
  balanceRemaining: number;
  reminderDate: string;
  period: 'MORNING' | 'AFTERNOON' | 'EVENING' | 'ANYTIME';
  note: string | null;
  status: 'PENDING' | 'DONE' | 'CANCELLED';
  createdByName: string;
  createdAt: string;
}

export interface BillReminderRequest {
  billId: number;
  reminderDate: string;
  period: string;
  note?: string;
}

@Injectable({ providedIn: 'root' })
export class BillReminderService {
  private apiUrl = `${environment.apiUrl}/bill-reminders`;

  constructor(private http: HttpClient) {}

  create(request: BillReminderRequest): Observable<BillReminderResponse> {
    return this.http.post<BillReminderResponse>(this.apiUrl, request);
  }

  getTodayAndOverdue(): Observable<BillReminderResponse[]> {
    return this.http.get<BillReminderResponse[]>(`${this.apiUrl}/today`);
  }

  getPending(): Observable<BillReminderResponse[]> {
    return this.http.get<BillReminderResponse[]>(`${this.apiUrl}/pending`);
  }

  getByBill(billId: number): Observable<BillReminderResponse[]> {
    return this.http.get<BillReminderResponse[]>(`${this.apiUrl}/bill/${billId}`);
  }

  markDone(id: number): Observable<BillReminderResponse> {
    return this.http.patch<BillReminderResponse>(`${this.apiUrl}/${id}/done`, {});
  }

  cancel(id: number): Observable<BillReminderResponse> {
    return this.http.patch<BillReminderResponse>(`${this.apiUrl}/${id}/cancel`, {});
  }
}