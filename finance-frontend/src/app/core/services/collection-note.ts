import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface CollectionNoteResponse {
  id: number;
  billId: number;
  billNumber: string;
  customerName: string;
  area: string;
  billBalance: number;
  amount: number;
  paymentType: 'CASH' | 'CHEQUE' | 'BANK_TRANSFER';
  status: 'PENDING' | 'MATCHED';
  collectedByName: string;
  collectedAt: string;
  notes?: string;
  chequeNumber?: string;
  bankName?: string;
  branchName?: string;
  chequeDate?: string;
  referenceNumber?: string;
  sourceEntryId?: number;
}

export interface CollectionNoteBulkItem {
  billId: number;
  amount: number;
}

export interface CollectionNoteBulkPayload {
  paymentType: 'CASH' | 'CHEQUE' | 'BANK_TRANSFER';
  notes?: string;
  chequeNumber?: string;
  bankName?: string;
  branchName?: string;
  chequeDate?: string;
  referenceNumber?: string;
  bills: CollectionNoteBulkItem[];
}

@Injectable({ providedIn: 'root' })
export class CollectionNoteService {
  private apiUrl = `${environment.apiUrl}/collection-notes`;

  constructor(private http: HttpClient) {}

  create(payload: {
    billId: number;
    amount: number;
    paymentType: 'CASH' | 'CHEQUE' | 'BANK_TRANSFER';
    notes?: string;
    chequeNumber?: string;
    bankName?: string;
    branchName?: string;
    chequeDate?: string;
    referenceNumber?: string;
    /** Photo of the bill. Optional on this screen, for every role. */
    receiptImageUrl?: string;
  }): Observable<CollectionNoteResponse> {
    return this.http.post<CollectionNoteResponse>(this.apiUrl, payload);
  }

  createBulk(payload: CollectionNoteBulkPayload): Observable<CollectionNoteResponse[]> {
    return this.http.post<CollectionNoteResponse[]>(`${this.apiUrl}/bulk`, payload);
  }

  getMyNotes(): Observable<CollectionNoteResponse[]> {
    return this.http.get<CollectionNoteResponse[]>(`${this.apiUrl}/my`);
  }

  getPendingNotes(): Observable<CollectionNoteResponse[]> {
    return this.http.get<CollectionNoteResponse[]>(`${this.apiUrl}/pending`);
  }

  getAllNotes(): Observable<CollectionNoteResponse[]> {
    return this.http.get<CollectionNoteResponse[]>(`${this.apiUrl}/all`);
  }

  updateNote(id: number, payload: {
    amount: number;
    paymentType: 'CASH' | 'CHEQUE' | 'BANK_TRANSFER';
    notes?: string;
  }): Observable<CollectionNoteResponse> {
    return this.http.patch<CollectionNoteResponse>(`${this.apiUrl}/${id}`, payload);
  }

  deleteNote(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
