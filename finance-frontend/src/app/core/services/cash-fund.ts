import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface CashFundRequest {
  amount: number;
  date: string;
  description?: string;
}

export interface CashFundResponse {
  id: number;
  amount: number;
  date: string;
  description?: string;
  addedByName: string;
  createdAt: string;
}

export interface CashBalanceResponse {
  totalFunded: number;
  totalSpent: number;
  balance: number;
}

@Injectable({ providedIn: 'root' })
export class CashFundService {
  private apiUrl = `${environment.apiUrl}/cash-fund`;

  constructor(private http: HttpClient) {}

  addFund(req: CashFundRequest): Observable<CashFundResponse> {
    return this.http.post<CashFundResponse>(this.apiUrl, req);
  }

  getBalance(): Observable<CashBalanceResponse> {
    return this.http.get<CashBalanceResponse>(`${this.apiUrl}/balance`);
  }

  getHistory(): Observable<CashFundResponse[]> {
    return this.http.get<CashFundResponse[]>(`${this.apiUrl}/history`);
  }
}