import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface CustomerResponse {
  id: number;
  name: string;
  phone?: string;
  area?: string;
  tier?: string;
  shopType?: string;
  active: boolean;
  createdAt: string;
}

export interface CustomerRequest {
  name: string;
}

@Injectable({ providedIn: 'root' })
export class CustomerService {
  private base = `${environment.apiUrl}/customers`;

  constructor(private http: HttpClient) {}

  getAll(): Observable<CustomerResponse[]> {
    return this.http.get<CustomerResponse[]>(this.base);
  }

  getActive(): Observable<CustomerResponse[]> {
    return this.http.get<CustomerResponse[]>(`${this.base}/active`);
  }

  create(req: CustomerRequest): Observable<CustomerResponse> {
    return this.http.post<CustomerResponse>(this.base, req);
  }

  update(id: number, req: CustomerRequest): Observable<CustomerResponse> {
    return this.http.put<CustomerResponse>(`${this.base}/${id}`, req);
  }

  activate(id: number): Observable<void> {
    return this.http.patch<void>(`${this.base}/${id}/activate`, {});
  }

  deactivate(id: number): Observable<void> {
    return this.http.patch<void>(`${this.base}/${id}/deactivate`, {});
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
