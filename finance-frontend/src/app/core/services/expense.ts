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
  receiptImageUrl?: string;
}

export interface ExpenseAdvanceResponse {
  id: number; business: string; targetType: 'DELIVERY_ROUTE' | 'COLLECTION_DAY';
  deliveryRunId?: number; deliveryRoute?: string; collectorId?: number; collectorName?: string;
  activityDate: string; amount: number; returnedAmount?: number; status: 'OPEN' | 'CLOSED';
  notes?: string; issuedByName: string; createdAt: string;
}

export interface ExpenseResponse {
  id: number;
  business: string;
  category: string;
  amount: number;
  date: string;
  description?: string;
  receiptImageUrl?: string;
  advanceId?: number;
  workerName?: string;
  status: string;
  enteredByName: string;
  reviewedByName?: string;
  reviewedAt?: string;
  createdAt: string;
}

export interface ExpenseBusFare {
  id: number; fromLocation: string; toLocation: string; amount: number; active: boolean;
}

@Injectable({ providedIn: 'root' })
export class ExpenseService {
  private apiUrl = `${environment.apiUrl}/expenses`;

  constructor(private http: HttpClient) {}

  create(req: ExpenseRequest): Observable<ExpenseResponse> {
    return this.http.post<ExpenseResponse>(this.apiUrl, req);
  }

  uploadImage(file: File): Observable<{ url: string }> {
    const data = new FormData(); data.append('file', file);
    return this.http.post<{ url: string }>(`${this.apiUrl}/upload-image`, data);
  }

  getAdvances(): Observable<ExpenseAdvanceResponse[]> {
    return this.http.get<ExpenseAdvanceResponse[]>(`${this.apiUrl}/advances`);
  }

  issueAdvance(req: Omit<ExpenseAdvanceResponse, 'id' | 'status' | 'returnedAmount' | 'issuedByName' | 'createdAt' | 'deliveryRoute' | 'collectorName'>): Observable<ExpenseAdvanceResponse> {
    return this.http.post<ExpenseAdvanceResponse>(`${this.apiUrl}/advances`, req);
  }

  settleAdvance(id: number, req: { returnedAmount: number; items: Array<{category:string;amount:number;description?:string;receiptImageUrl?:string;busFareId?:number}> }): Observable<ExpenseAdvanceResponse> {
    return this.http.post<ExpenseAdvanceResponse>(`${this.apiUrl}/advances/${id}/settle`, req);
  }

  getBusFares(): Observable<ExpenseBusFare[]> {
    return this.http.get<ExpenseBusFare[]>(`${this.apiUrl}/bus-fares`);
  }

  addBusFare(req: { fromLocation: string; toLocation: string; amount: number }): Observable<ExpenseBusFare> {
    return this.http.post<ExpenseBusFare>(`${this.apiUrl}/bus-fares`, req);
  }

  updateBusFare(id: number, req: { fromLocation: string; toLocation: string; amount: number }): Observable<ExpenseBusFare> {
    return this.http.put<ExpenseBusFare>(`${this.apiUrl}/bus-fares/${id}`, req);
  }

  setBusFareActive(id: number, active: boolean): Observable<ExpenseBusFare> {
    return this.http.patch<ExpenseBusFare>(`${this.apiUrl}/bus-fares/${id}/active`, { active });
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
