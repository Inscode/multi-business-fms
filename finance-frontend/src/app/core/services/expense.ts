import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ExpenseRequest {
  business: string;
  category: string;
  amount: number;
  date: string;
  description?: string;
  workerId?: number;
}

export interface ExpenseResponse {
  id: number;
  business: string;
  category: string;
  amount: number;
  date: string;
  description?: string;
  workerName?: string;
  status: string;
  enteredByName: string;
  reviewedByName?: string;
  reviewedAt?: string;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class ExpenseService {
  private apiUrl = `${environment.apiUrl}/expenses`;

  constructor(private http: HttpClient) {}

  create(req: ExpenseRequest): Observable<ExpenseResponse> {
    return this.http.post<ExpenseResponse>(this.apiUrl, req);
  }

  getAll(filters?: {
    business?: string;
    category?: string;
    status?: string;
    from?: string;
    to?: string;
  }): Observable<ExpenseResponse[]> {
    let params = new HttpParams();
    if (filters?.business) params = params.set('business', filters.business);
    if (filters?.category) params = params.set('category', filters.category);
    if (filters?.status)   params = params.set('status',   filters.status);
    if (filters?.from)     params = params.set('from',     filters.from);
    if (filters?.to)       params = params.set('to',       filters.to);
    return this.http.get<ExpenseResponse[]>(this.apiUrl, { params });
  }

  review(id: number): Observable<ExpenseResponse> {
    return this.http.patch<ExpenseResponse>(`${this.apiUrl}/${id}/review`, {});
  }

  getPendingCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.apiUrl}/pending-count`);
  }
}