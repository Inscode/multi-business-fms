import { CommonModule, DatePipe, DecimalPipe, LowerCasePipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
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
import { BillReturnResponse, BillReturnService } from '../../../core/services/bill-return';
import { RequestEditDialog } from '../../../shared/request-edit-dialog/request-edit-dialog';

@Component({
  selector: 'app-bill-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatDividerModule,
    MatProgressSpinnerModule,
    MatMenuModule,
    MatDialogModule,
    DecimalPipe,
    LowerCasePipe,
    DatePipe,
  ],
  templateUrl: './bill-detail.html',
  styleUrl: './bill-detail.scss',
})
export class BillDetail implements OnInit {
  bill: BillResponse | null = null;
  payments: PaymentResponse[] = [];
  returns: BillReturnResponse[] = [];
  workers: WorkerResponse[] = [];
  loading = true;
  paymentsLoading = true;
  returnsLoading = false;
  error = false;

  paymentColumns = ['paymentDate', 'amount', 'type', 'status', 'enteredBy', 'actions'];

  get isAccountant(): boolean { return this.auth.getRole() === 'ACCOUNTANT'; }
  get isOwner(): boolean { return this.auth.getRole() === 'OWNER'; }
  get isMainAccountant(): boolean { return this.auth.getRole() === 'MAIN_ACCOUNTANT'; }
  get isAdmin(): boolean { return this.auth.getRole() === 'ADMIN'; }

  get canAssign(): boolean {
    return !this.isOwner && (this.isAccountant || this.isAdmin) &&
      ['CREATED', 'ASSIGNED', 'STORE_RECEIVED'].includes(this.bill?.status ?? '') &&
      !this.bill?.fullyPaid;
  }

  get canMarkReceived(): boolean {
    return !this.isOwner && (this.isAccountant || this.isAdmin || this.isMainAccountant) &&
      ['ASSIGNED', 'SHOP_RECEIVED'].includes(this.bill?.status ?? '');
  }

  get canMarkShopReceived(): boolean {
    return !this.isOwner && (this.isAccountant || this.isAdmin || this.isMainAccountant) &&
      ['CREATED', 'ASSIGNED', 'STORE_RECEIVED'].includes(this.bill?.status ?? '');
  }

  get canEnterPayment(): boolean {
    return !this.isOwner && (this.isAccountant || this.isAdmin || this.isMainAccountant) &&
      !this.bill?.fullyPaid && this.bill?.status !== 'CANCELLED' &&
      this.bill?.status !== 'COMPLETED';
  }

  get canRequestEdit(): boolean {
    return this.isAccountant || this.isMainAccountant;
  }

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private billService: Bill,
    private paymentService: Payment,
    private billReturnService: BillReturnService,
    private workerService: Worker,
    private auth: Auth,
    private cdr: ChangeDetectorRef,
    private dialog: MatDialog,
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
        this.loadReturns(id);
      },
      error: () => {
        this.error = true;
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
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
    });
  }

  private loadReturns(billId: number): void {
    this.returnsLoading = true;
    this.billReturnService.getForBill(billId).subscribe({
      next: (r) => {
        this.returns = r;
        this.returnsLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.returnsLoading = false;
        this.cdr.detectChanges();
      },
    });
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

  markShopReceived(): void {
    if (!this.bill) return;
    this.billService.markShopReceived(this.bill.id).subscribe({
      next: (b) => {
        this.bill = b;
        this.cdr.detectChanges();
      },
      error: () => alert('Failed to mark shop received.')
    });
  }

  enterPayment(): void {
    if (!this.bill) return;
    this.router.navigate(['/payments/enter'], {
      state: { preselectedBill: this.bill }
    });
  }

  editBill(): void {
    if (!this.bill) return;
    this.router.navigate(['/bills/create'], {
      state: { editingBill: this.bill }
    });
  }

  openBillEditRequest(): void {
    if (!this.bill) return;
    this.dialog.open(RequestEditDialog, {
      data: {
        type: 'BILL',
        targetId: this.bill.id,
        targetRef: this.bill.billNumber,
        current: this.bill,
      },
      width: '520px',
    }).afterClosed().subscribe(submitted => {
      if (submitted) this.cdr.detectChanges();
    });
  }

  openPaymentEditRequest(payment: PaymentResponse): void {
    this.dialog.open(RequestEditDialog, {
      data: {
        type: 'PAYMENT',
        targetId: payment.id,
        targetRef: `${payment.billNumber} / ${payment.paymentDate}`,
        current: payment,
      },
      width: '520px',
    }).afterClosed().subscribe(submitted => {
      if (submitted) this.cdr.detectChanges();
    });
  }

  deleteBill(): void {
    if (!this.bill) return;
    const ref = this.bill.billNumber;
    if (!confirm(`Delete bill ${ref}? This cannot be undone. Bills with confirmed payments cannot be deleted.`)) return;
    this.billService.deleteBill(this.bill.id).subscribe({
      next: () => this.router.navigate(['/bills']),
      error: (err) => alert(err?.error?.message ?? 'Failed to delete bill.'),
    });
  }

  deletePayment(payment: PaymentResponse): void {
    if (!confirm(`Delete payment of Rs ${payment.paymentAmount} on ${payment.paymentDate}? This cannot be undone.`)) return;
    this.paymentService.deletePayment(payment.id).subscribe({
      next: () => this.loadPayments(this.bill!.id),
      error: (err) => alert(err?.error?.message ?? 'Failed to delete payment.'),
    });
  }

  goBack(): void {
    this.router.navigate(['/bills']);
  }
}