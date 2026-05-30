import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ReturnProductResponse {
  id: number;
  business: string;
  name: string;
  unitPrice: number;
  active: boolean;
  createdAt: string;
}

export interface ReturnProductRequest {
  business: string;
  name: string;
  unitPrice: number;
}

@Injectable({ providedIn: 'root' })
export class ReturnProductService {
  private apiUrl = `${environment.apiUrl}/return-products`;

  constructor(private http: HttpClient) {}

  getAll(): Observable<ReturnProductResponse[]> {
    return this.http.get<ReturnProductResponse[]>(this.apiUrl);
  }

  getByBusiness(business: string): Observable<ReturnProductResponse[]> {
    return this.http.get<ReturnProductResponse[]>(`${this.apiUrl}/by-business/${business}`);
  }

  create(req: ReturnProductRequest): Observable<ReturnProductResponse> {
    return this.http.post<ReturnProductResponse>(this.apiUrl, req);
  }

  update(id: number, req: ReturnProductRequest): Observable<ReturnProductResponse> {
    return this.http.put<ReturnProductResponse>(`${this.apiUrl}/${id}`, req);
  }

  deactivate(id: number): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/${id}/deactivate`, {});
  }

  activate(id: number): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/${id}/activate`, {});
  }
}