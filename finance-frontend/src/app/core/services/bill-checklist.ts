import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ChecklistNoteRequest {
  itemName: string;
  count: number;
  note?: string;
  entryDate?: string; // YYYY-MM-DD, defaults to today
}

export interface ChecklistNoteResponse {
  id: number;
  itemName: string;
  count: number;
  note?: string;
  entryDate: string;
  createdByName: string;
  createdAt: string;
}

export interface ChecklistVerifyRow {
  itemName: string;
  carryForward: number;
  carryFromDate?: string;
  todayNotes: number;
  total: number;
  markedEntered: number;
  balance: number;
}

export interface ChecklistClosingRequest {
  closingDate: string;
  items: { itemName: string; markedEntered: number }[];
}

export interface ChecklistDashboardSummary {
  totalNotedToday: number;
  totalEnteredToday: number;
  pendingBalance: number;
  itemsWithBalance: number;
}

@Injectable({ providedIn: 'root' })
export class BillChecklistService {
  private api = `${environment.apiUrl}/bill-checklist`;

  constructor(private http: HttpClient) {}

  addNote(req: ChecklistNoteRequest): Observable<ChecklistNoteResponse> {
    return this.http.post<ChecklistNoteResponse>(`${this.api}/notes`, req);
  }

  getNotesForDate(date: string): Observable<ChecklistNoteResponse[]> {
    const params = new HttpParams().set('date', date);
    return this.http.get<ChecklistNoteResponse[]>(`${this.api}/notes`, { params });
  }

  deleteNote(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/notes/${id}`);
  }

  getVerifyView(date: string): Observable<ChecklistVerifyRow[]> {
    const params = new HttpParams().set('date', date);
    return this.http.get<ChecklistVerifyRow[]>(`${this.api}/verify`, { params });
  }

  saveClosing(req: ChecklistClosingRequest): Observable<void> {
    return this.http.post<void>(`${this.api}/close`, req);
  }

  getDashboardSummary(): Observable<ChecklistDashboardSummary> {
    return this.http.get<ChecklistDashboardSummary>(`${this.api}/dashboard-summary`);
  }
}
