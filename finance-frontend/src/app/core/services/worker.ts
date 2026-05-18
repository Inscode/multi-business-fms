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
}
