import { CommonModule, DecimalPipe, LowerCasePipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTabsModule } from '@angular/material/tabs';
import { Router } from '@angular/router';
import { Dashboard, ShopBill, ShopDashboardData } from '../../../../core/services/dashboard';
import { Auth } from '../../../../core/services/auth';
import { Bill } from '../../../../core/services/bill';
import { Payment, PaymentResponse } from '../../../../core/services/payment';
import { Worker, WorkerResponse } from '../../../../core/services/worker';

@Component({
  selector: 'app-shop-dashboard',
  imports: [
    CommonModule,
    DecimalPipe,
    LowerCasePipe,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTabsModule,
    MatMenuModule,
  ],
  templateUrl: './shop-dashboard.html',
  styleUrl: './shop-dashboard.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShopDashboard implements OnInit {
  data: ShopDashboardData | null = null;
  loading = true;
  error = false;

  payments: PaymentResponse[] = [];
  paymentsLoading = false;
  paymentsError = false;

  workers: WorkerResponse[] = [];

  get greeting(): string {
    const h = new Date().getHours();
    if (h < 12) return 'Good morning';
    if (h < 17) return 'Good afternoon';
    return 'Good evening';
  }

  constructor(
    private dashboardService: Dashboard,
    private paymentService: Payment,
    private billService: Bill,
    private workerService: Worker,
    public auth: Auth,
    private cdr: ChangeDetectorRef,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.load();
    this.loadWorkers();
    this.loadPayments();
  }

  load(): void {
    this.loading = true;
    this.error = false;
    this.dashboardService.getShopDashboard().subscribe({
      next: (d) => {
        this.data = d;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.error = true;
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  loadPayments(): void {
    this.paymentsLoading = true;
    this.paymentsError = false;
    this.paymentService.getMyEnteredPayments().subscribe({
      next: (p) => {
        this.payments = p;
        this.paymentsLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.paymentsError = true;
        this.paymentsLoading = false;
        this.cdr.detectChanges();
      },
    });
  }

  private loadWorkers(): void {
    this.workerService.getAllWorkers().subscribe({
      next: (w) => { this.workers = w.filter(x => x.active); this.cdr.detectChanges(); },
      error: () => {},
    });
  }

  recordPayment(bill: ShopBill): void {
    this.router.navigate(['/payments/enter'], { state: { preselectedBill: bill } });
  }

  markShopReceived(id: number): void {
    this.billService.markShopReceived(id).subscribe({
      next: () => this.load(),
      error: () => alert('Failed to mark as received.'),
    });
  }

  assignWorker(billId: number, workerId: number): void {
    this.billService.assignBill(billId, workerId).subscribe({
      next: () => this.load(),
      error: () => alert('Failed to assign worker.'),
    });
  }

  canShopReceive(bill: ShopBill): boolean {
    return ['CREATED', 'ASSIGNED', 'SHOP_WORKER_ASSIGNED', 'STORE_RECEIVED'].includes(bill.status);
  }

  canAssignWorker(bill: ShopBill): boolean {
    return ['ASSIGNED', 'SHOP_RECEIVED', 'SHOP_WORKER_ASSIGNED'].includes(bill.status);
  }

  onTabChange(index: number): void {
    if (index === 1) this.loadPayments();
  }
}