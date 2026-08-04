import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { Customer } from '../models/models';
import { environment } from '../../../../../environments/environment';

/**
 * Adapter over the FMS customer API — the invoicing module shares the FMS customer
 * base rather than keeping its own copy. customerCode/address are the wholesale
 * fields; they're nullable here until codes are assigned to FMS customers.
 */
@Injectable({ providedIn: 'root' })
export class CustomerService {
  private base = `${environment.apiUrl}/customers`;
  constructor(private http: HttpClient) {}

  list(): Observable<Customer[]> {
    return this.http.get<any[]>(`${this.base}/active`).pipe(
      map(list => list.map(c => ({
        id: c.id,
        customerCode: c.customerCode ?? '',
        name: c.name,
        address: c.address ?? c.area ?? '',
        phone: c.phone ?? undefined,
        city: c.area ?? undefined,
        active: c.active ?? true,
        reviewed: true,
      } as Customer))),
    );
  }
}
