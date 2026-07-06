import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface WorkerAverageHours {
  workerId: number;
  workerName: string;
  workerType: string;
  expectedWorkingDays: number;
  presentDays: number;
  halfDays: number;
  absentDays: number;
  holidayDays: number;
  totalHoursLogged: number;
  averageHoursPerDay: number;
}

export interface WorkerAttendanceRecord {
  id: number;
  workerId: number;
  workerName: string;
  date: string;
  attendanceType: 'PRESENT' | 'HALF_DAY' | 'ABSENT' | 'HOLIDAY';
  hoursWorked: number | null;
  notes: string | null;
}

export interface CompanyHoliday {
  id: number;
  date: string;
  name: string;
  holidayType: 'POYA' | 'NATIONAL' | 'PUBLIC' | 'OTHER';
  appliesToAll: boolean;
  workerNames: string[];
  createdAt: string;
}

export interface WorkerTimeEntry {
  id: number;
  workerId: number;
  workerName: string;
  date: string;
  clockIn: string;     // "HH:mm:ss"
  clockOut: string | null;
  workedMinutes: number;
  notes: string | null;
}

export interface TimeEntryRequest {
  workerId: number;
  date: string;
  clockIn: string;
  clockOut?: string | null;
  notes?: string | null;
}

export interface AttendanceRequest {
  workerId: number;
  date: string;
  attendanceType: string;
  notes?: string | null;
}

export interface HolidayRequest {
  date: string;
  name: string;
  holidayType: string;
  appliesToAll: boolean;
  workerIds?: number[];
}

@Injectable({ providedIn: 'root' })
export class TimeLogService {
  private base = `${environment.apiUrl}/admin/time-log`;

  constructor(private http: HttpClient) {}

  getAverageHours(from: string, to: string): Observable<WorkerAverageHours[]> {
    return this.http.get<WorkerAverageHours[]>(`${this.base}/average`, { params: { from, to } });
  }

  recordAttendance(req: AttendanceRequest): Observable<WorkerAttendanceRecord> {
    return this.http.post<WorkerAttendanceRecord>(`${this.base}/attendance`, req);
  }

  getAttendanceForDate(date: string): Observable<WorkerAttendanceRecord[]> {
    return this.http.get<WorkerAttendanceRecord[]>(`${this.base}/attendance`, { params: { date } });
  }

  getWorkerAttendance(workerId: number, from: string, to: string): Observable<WorkerAttendanceRecord[]> {
    return this.http.get<WorkerAttendanceRecord[]>(`${this.base}/attendance/worker/${workerId}`, { params: { from, to } });
  }

  createHoliday(req: HolidayRequest): Observable<CompanyHoliday> {
    return this.http.post<CompanyHoliday>(`${this.base}/holidays`, req);
  }

  getHolidays(from: string, to: string): Observable<CompanyHoliday[]> {
    return this.http.get<CompanyHoliday[]>(`${this.base}/holidays`, { params: { from, to } });
  }

  deleteHoliday(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/holidays/${id}`);
  }

  // Time entries
  addTimeEntry(req: TimeEntryRequest): Observable<WorkerTimeEntry> {
    return this.http.post<WorkerTimeEntry>(`${this.base}/entries`, req);
  }

  updateTimeEntry(id: number, req: TimeEntryRequest): Observable<WorkerTimeEntry> {
    return this.http.put<WorkerTimeEntry>(`${this.base}/entries/${id}`, req);
  }

  deleteTimeEntry(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/entries/${id}`);
  }

  getEntriesForDate(date: string): Observable<WorkerTimeEntry[]> {
    return this.http.get<WorkerTimeEntry[]>(`${this.base}/entries`, { params: { date } });
  }

  getWorkerEntries(workerId: number, date: string): Observable<WorkerTimeEntry[]> {
    return this.http.get<WorkerTimeEntry[]>(`${this.base}/entries/worker/${workerId}`, { params: { date } });
  }

  toggleWorkerTimeLog(workerId: number): Observable<import('./worker').WorkerResponse> {
    return this.http.patch<import('./worker').WorkerResponse>(`${this.base}/workers/${workerId}/toggle-log`, {});
  }
}
