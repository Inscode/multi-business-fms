import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface EditRequestResponse {
  id: number;
  type: 'BILL' | 'PAYMENT';
  targetId: number;
  targetRef: string;
  requestedChanges: string;
  reason: string | null;
  proofImageUrl: string | null;
  requestedByName: string;
  requestedAt: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  reviewedByName: string | null;
  reviewedAt: string | null;
  rejectionReason: string | null;
}

export interface CreateEditRequestPayload {
  /** The bill photographed. Required by the server when the change moves an amount. */
  proofImageUrl?: string;

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

  /**
   * Uploads the bill photograph for a request that changes an amount.
   *
   * <p>Its own folder in ImageKit, kept apart from payments and returns: these are
   * evidence for a figure somebody asked to change, and are worth as long a life as the
   * bill itself rather than the few weeks a task photo gets.
   */
  uploadImage(file: File): Observable<string> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<{ url: string }>(`${this.apiUrl}/upload-image`, form)
      .pipe(map(r => r.url));
  }

  getAll(): Observable<EditRequestResponse[]> {
    return this.http.get<EditRequestResponse[]>(this.apiUrl);
  }

  getPending(): Observable<EditRequestResponse[]> {
    return this.http.get<EditRequestResponse[]>(`${this.apiUrl}/pending`);
  }

  approve(id: number, continuationNumbers: string[] = []): Observable<EditRequestResponse> {
    return this.http.patch<EditRequestResponse>(`${this.apiUrl}/${id}/approve`, { continuationNumbers });
  }

  reject(id: number, reason: string): Observable<EditRequestResponse> {
    return this.http.patch<EditRequestResponse>(`${this.apiUrl}/${id}/reject`, { reason });
  }
}
