import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BillReminderResponse } from './bill-reminder';

export interface AccountantDashboardData {
  totalBillsToday: number;
  assignedBills: number;
  inShopBills: number;
  receivedBills: number;
  pendingPayments: number;
  recentBills: BillSummary[];
  unassignedBills: BillSummary[];
  todayReminders: BillReminderResponse[];
}

export interface OwnerDashboardData {
  assignedBills: number;
  inFieldBills: number;
  inShopBills: number;
  awaitingConfirmation: number;
  pendingBills: number;
  totalOutstanding: number;
  cashPending: number;
  cashSerious: number;

  raincoTodayBills: number;
  stationeryTodayBills: number;
  plasticTodayBills: number;

  raincoTodayPayments: number;
  stationeryTodayPayments: number;
  plasticTodayPayments: number;

  pendingPayments: PaymentSummary[];
  assignedBillList: BillSummary[];
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
  collectedByOwnerName: string | null;
  collectedByWorkerName: string | null;
  collectorNote: string | null;
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

export interface DsoTrendEntry {
  month: string;              // "YYYY-MM"
  avgCollectionDays: number;
  settledBillCount: number;
}

export interface CollectorPerformanceEntry {
  workerId: number;
  workerName: string;
  totalCollected: number;
  paymentCount: number;
  avgDaysToCollect: number;
  partialRate: number;        // 0..1
}

export interface UpcomingChequeEntry {
  chequeDate: string;
  totalAmount: number;
  count: number;
}

export interface StaleChequeEntry {
  chequeDate: string;
  amount: number;
  customerName: string;
  billNumber: string;
}

export interface CustomerRiskEntry {
  customerName: string;
  area: string | null;
  partialCount: number;
  returnedCount: number;
  currentOutstanding: number;
  riskScore: number;
}

export interface PaymentMixEntry {
  date: string;
  cash: number;
  cheque: number;
  bankTransfer: number;
  total: number;
}

export interface CollectionHealthData {
  dsoTrend?: DsoTrendEntry[];
  collectorLeaderboard?: CollectorPerformanceEntry[];
  upcomingCheques?: UpcomingChequeEntry[];
  staleCheques?: StaleChequeEntry[];
  riskyCustomers?: CustomerRiskEntry[];
  paymentMix?: PaymentMixEntry[];
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

  getCollectionHealth(business: string = 'RAINCO'): Observable<CollectionHealthData> {
    const params = new HttpParams().set('business', business);
    return this.http.get<CollectionHealthData>(`${this.apiUrl}/collection-health`, { params });
  }

  getUpcomingCheques(business: string, from: string, to: string): Observable<UpcomingChequeEntry[]> {
    const params = new HttpParams().set('business', business).set('from', from).set('to', to);
    return this.http.get<UpcomingChequeEntry[]>(`${this.apiUrl}/upcoming-cheques`, { params });
  }

}
