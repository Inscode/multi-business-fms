import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface EditRequestResponse {
  id: number;
  type: 'BILL' | 'PAYMENT';
  targetId: number;
  targetRef: string;
  requestedChanges: string;
  reason: string | null;
  requestedByName: string;
  requestedAt: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  reviewedByName: string | null;
  reviewedAt: string | null;
  rejectionReason: string | null;
}

export interface CreateEditRequestPayload {
  type: 'BILL' | 'PAYMENT';
  targetId: number;
  targetRef: string;
  requestedChanges: string;
  reason?: string;
}

@Injectable({ providedIn: 'root' })
export class EditRequestService {
  private apiUrl = `${environment.apiUrl}/edit-requests`;

  constructor(private http: HttpClient) {}

  create(payload: CreateEditRequestPayload): Observable<EditRequestResponse> {
    return this.http.post<EditRequestResponse>(this.apiUrl, payload);
  }

  getAll(): Observable<EditRequestResponse[]> {
    return this.http.get<EditRequestResponse[]>(this.apiUrl);
  }

  getPending(): Observable<EditRequestResponse[]> {
    return this.http.get<EditRequestResponse[]>(`${this.apiUrl}/pending`);
  }

  approve(id: number): Observable<EditRequestResponse> {
    return this.http.patch<EditRequestResponse>(`${this.apiUrl}/${id}/approve`, {});
  }

  reject(id: number, reason: string): Observable<EditRequestResponse> {
    return this.http.patch<EditRequestResponse>(`${this.apiUrl}/${id}/reject`, { reason });
  }
}