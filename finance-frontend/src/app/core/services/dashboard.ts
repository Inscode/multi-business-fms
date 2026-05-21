import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface AccountantDashboardData {
  totalBillsToday: number;
  assignedBills: number;
  inShopBills: number;
  receivedBills: number;
  pendingPayments: number;
  recentBills: BillSummary[];
  unassignedBills: BillSummary[];
}

export interface OwnerDashboardData {
  unassignedBills: number;
  inFieldBills: number;
  inShopBills: number;
  awaitingConfirmation: number;
  fullyPaidToday: number;
  totalOutstanding: number;
  pendingPayments: PaymentSummary[];
  unassignedBillList: BillSummary[];
}

export interface BillSummary {
  id: number;
  billNumber: string;
  customerName: string;
  totalAmount: number;
  workerName: string | null;
  status: string;
}

export interface PaymentSummary {
  id: number;
  billNumber: string;
  customerName: string;
  amount: number;
  paymentType: string;
  enteredByName: string;
  paymentDate: string;
  status: string;
}


export interface ShopDashboardData {
  shopReceivedBills: number;
  shopWorkerAssignedBills: number;
  pendingPayments: number;
  bills: ShopBill[];
}

export interface ShopBill {
  id: number;
  billNumber: string;
  business: string;
  customerName: string;
  area: string;
  totalAmount: number;
  amountPaid: number;
  balanceRemaining: number;
  status: string;
  workerName: string | null;
  billDate: string;
  notes: string;
}

@Injectable({
  providedIn: 'root',
})
export class Dashboard {
  private apiUrl = `${environment.apiUrl}/dashboard`;

  constructor(private http: HttpClient) {}

  getAccountantDashboard(): Observable<AccountantDashboardData> {
    return this.http.get<AccountantDashboardData>(`${this.apiUrl}/accountant`);
  }

  getOwnerDashboard(business: string): Observable<OwnerDashboardData> {
    const params = new HttpParams().set('business', business);
    return this.http.get<OwnerDashboardData>(`${this.apiUrl}/owner`, {params});
  }

  getShopDashboard(): Observable<ShopDashboardData> {
    return this.http.get<ShopDashboardData>(`${this.apiUrl}/shop`);
  }

}
