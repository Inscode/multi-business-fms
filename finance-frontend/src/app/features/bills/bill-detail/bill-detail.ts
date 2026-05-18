import { CommonModule, DatePipe, DecimalPipe, LowerCasePipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Bill, BillResponse } from '../../../core/services/bill';
import { Worker, WorkerResponse } from '../../../core/services/worker';
import { Auth } from '../../../core/services/auth';
import { Payment, PaymentResponse } from '../../../core/services/payment';

@Component({
  selector: 'app-bill-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatDividerModule,
    MatProgressSpinnerModule,
    MatMenuModule,
    DecimalPipe,
    LowerCasePipe,
    DatePipe,
  ],
  templateUrl: './bill-detail.html',
  styleUrl: './bill-detail.scss',
})
export class BillDetail implements OnInit{
  bill: BillResponse | null = null;
  payments: PaymentResponse[] = [];
  workers: WorkerResponse[] = [];
  loading = true;
  paymentsLoading = true;
  error = false;

  paymentColumns = ['paymentDate', 'amount', 'type', 'status', 'enteredBy'];

  get isAccountant(): boolean { return this.auth.getRole() === 'ACCOUNTANT';}
  get isOwner(): boolean { return this.auth.getRole() === 'OWNER';}
  get isAdmin(): boolean { return this.auth.getRole() === 'ADMIN';}

  get canAssign(): boolean {
    return (this.isAccountant || this.isAdmin) && 
    ['CREATED', 'ASSIGNED', 'STORE_RECEIVED'].includes(this.bill?.status?? '') && 
    !this.bill?.fullyPaid;
  }

  get canMarkReceived(): boolean {
    return (this.isAccountant || this.isAdmin) && 
    ['ASSIGNED', 'SHOP_RECEIVED'].includes(this.bill?.status ?? '');
  }

  get canEnterPayment(): boolean {
    return (this.isAccountant || this.isAdmin) && 
    !this.bill?.fullyPaid && this.bill?.status !== 'CANCELLED' &&
    this.bill?.status !== 'COMPLETED';
  }

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private billService: Bill,
    private paymentService: Payment,
    private workerService: Worker,
    private auth: Auth,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.load(id);
    this.loadWorkers();
  }

  load(id: number): void {
    this.loading = true;
    this.error = false;

    this.billService.getBillById(id).subscribe({
      next: (b) => {
        this.bill = b;
        this.loading = false;
        this.cdr.detectChanges();
        this.loadPayments(id);
      },
      error: () => {
        this.error = true;
        this.loading = false;
        this.cdr.detectChanges();
      }
    })
  }

  private loadPayments(billId: number): void {
    this.paymentsLoading = true;
    this.paymentService.getPaymentsByBill(billId).subscribe({
      next: (p) => {
        this.payments = p;
        this.paymentsLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.paymentsLoading = false;
        this.cdr.detectChanges();
      }
    })
  }

  private loadWorkers(): void {
    this.workerService.getAllWorkers().subscribe({
      next: (w) => this.workers = w.filter(w => w.active),
      error: () => this.workers = []
    });
  }

  assignBill(workerId: number): void {
    if (!this.bill) return;
    this.billService.assignBill(this.bill.id, workerId).subscribe({
      next: (b) => {
        this.bill = b;
        this.cdr.detectChanges();
      },
      error: () => alert('Failed to assign bill.')
    });
  }

  markReceived(): void {
    if (!this.bill) return;
    this.billService.markReceived(this.bill.id).subscribe({
      next: (b) => {
        this.bill = b;
        this.cdr.detectChanges();
      },
      error: () => alert('Failed to mark received.')
    });
  }

  enterPayment(): void {
    if (!this.bill) return;
    this.router.navigate(['/payments/enter'], {
      state: { preselectedBill: this.bill }
    });
  }

  goBack(): void {
    this.router.navigate(['/bills']);
  }
}
