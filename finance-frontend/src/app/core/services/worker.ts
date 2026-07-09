import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface WorkerResponse {
  id: number;
  fullName: string;
  workerType: string;
  baseSalary: number;
  active: boolean;
  joinedDate: string;
  notes: string;
  createdAt: string;
  timeLogActive: boolean;
  billAssignable: boolean;
  normalWorkingHours: number;
}

export interface WorkerRequest {
  fullName: string;
  workerType: string;
  baseSalary: number;
  joinedDate: string;
  notes?: string;
  normalWorkingHours?: number;
}

@Injectable({
  providedIn: 'root',
})
export class Worker {
  private apiUrl = `${environment.apiUrl}/workers`;

  constructor(private http: HttpClient) {}

  getAllWorkers(): Observable<WorkerResponse[]> {
    return this.http.get<WorkerResponse[]>(this.apiUrl);
  }

  getWorkersByType(type: string): Observable<WorkerResponse[]> {
    return this.http.get<WorkerResponse[]>(`${this.apiUrl}/worker-type/${type}`);
  }

  createWorker(payload: WorkerRequest): Observable<WorkerResponse> {
    return this.http.post<WorkerResponse>(this.apiUrl, payload);
  }

  updateWorker(id: number, payload: WorkerRequest): Observable<WorkerResponse> {
    return this.http.put<WorkerResponse>(`${this.apiUrl}/${id}`, payload);
  }

  activateWorker(id: number): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/${id}/activate`, {});
  }

  deactivateWorker(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  toggleBillAssignable(id: number): Observable<WorkerResponse> {
    return this.http.patch<WorkerResponse>(`${this.apiUrl}/${id}/toggle-bill-assignable`, {});
  }
}
