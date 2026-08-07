import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CategoryType } from '../models/models';
import { environment } from '../../../../../environments/environment';

export type GrnStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface GrnLine {
  id: number;
  itemId: number;
  itemCode: string;
  itemDescription: string;
  brandName: string | null;
  qty: number;
  unitCost: number | null;
  lineTotal: number | null;
}

export interface Grn {
  id: number;
  grnNo: string;
  category: CategoryType;
  supplierName: string | null;
  receivedDate: string;
  status: GrnStatus;
  rejectionReason: string | null;
  notes: string | null;
  submittedBy: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  createdAt: string;
  totalCost: number | null;
  totalQty: number | null;
  lines: GrnLine[];
}

export interface GrnRequest {
  category: CategoryType;
  supplierName?: string;
  receivedDate: string;
  notes?: string;
  lines: { itemId: number; qty: number; unitCost?: number }[];
}

@Injectable({ providedIn: 'root' })
export class GrnService {
  private base = `${environment.apiUrl}/invoicing/grn`;

  constructor(private http: HttpClient) {}

  list(status?: GrnStatus): Observable<Grn[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);
    return this.http.get<Grn[]>(this.base, { params });
  }

  create(req: GrnRequest): Observable<Grn> {
    return this.http.post<Grn>(this.base, req);
  }

  approve(id: number): Observable<Grn> {
    return this.http.post<Grn>(`${this.base}/${id}/approve`, {});
  }

  reject(id: number, reason: string): Observable<Grn> {
    return this.http.post<Grn>(`${this.base}/${id}/reject`, {}, {
      params: new HttpParams().set('reason', reason),
    });
  }
}
