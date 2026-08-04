import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Brand } from '../models/models';
import { environment } from '../../../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class BrandService {
  private base = `${environment.apiUrl}/invoicing/brands`;
  constructor(private http: HttpClient) {}

  list(category?: string): Observable<Brand[]> {
    let params = new HttpParams();
    if (category) params = params.set('category', category);
    return this.http.get<Brand[]>(this.base, { params });
  }

  create(req: any): Observable<Brand> {
    return this.http.post<Brand>(this.base, req);
  }

  update(id: number, req: any): Observable<Brand> {
    return this.http.put<Brand>(`${this.base}/${id}`, req);
  }
}
