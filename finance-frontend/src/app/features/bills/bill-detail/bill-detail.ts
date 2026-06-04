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
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { FormsModule, ReactiveFormsModule, FormControl } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Bill, BillResponse } from '../../../core/services/bill';
import { Worker, WorkerResponse } from '../../../core/services/worker';
import { Auth } from '../../../core/services/auth';
import { Payment, PaymentResponse } from '../../../core/services/payment';
import { BillReturnResponse, BillReturnService } from '../../../core/services/bill-return';
import { RequestEditDialog } from '../../../shared/request-edit-dialog/request-edit-dialog';
import { BillStockStatus, ReturnProductResponse, StockService } from '../../../core/services/stock';

@Component({
  selector: 'app-bill-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    RouterLink,
    FormsModule,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatDividerModule,
    MatProgressSpinnerModule,
    MatMenuModule,
    MatDialogModule,
    MatTooltipModule,
    MatAutocompleteModule,
    MatFormFieldModule,
    MatInputModule,
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
  stockStatus: BillStockStatus | null = null;
  loading = true;
  paymentsLoading = true;
  returnsLoading = false;
  stockLoading = false;
  error = false;

  paymentColumns = ['paymentDate', 'amount', 'type', 'status', 'enteredBy', 'actions'];
  stockItemColumns = ['productName', 'quantity', 'unitPrice', 'lineTotal'];
  comparisonColumns = ['productName', 'systemQty', 'childQty', 'diff'];

  // Reference item entry (for SYSTEM linking bills)
  allProducts: ReturnProductResponse[] = [];
  filteredRefProducts: ReturnProductResponse[] = [];
  refProductCtrl = new FormControl<ReturnProductResponse | string | null>(null);
  refQtyCtrl = new FormControl<number | null>(null);
  refLineItems: { productId: number; productName: string; quantity: number; unitPrice: number }[] = [];
  savingRefItems = false;
  refItemSuccess = '';
  refItemError = '';

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
    private stockService: StockService,
    private cdr: ChangeDetectorRef,
    private dialog: MatDialog,
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.load(id);
    this.loadWorkers();
    this.loadProducts();

    this.refProductCtrl.valueChanges.subscribe(val => {
      if (typeof val === 'string') {
        const s = val.toLowerCase();
        this.filteredRefProducts = s
          ? this.allProducts.filter(p => p.name.toLowerCase().includes(s))
          : [];
      } else {
        this.filteredRefProducts = [];
      }
      this.cdr.detectChanges();
    });
  }

  get selectedRefProduct(): ReturnProductResponse | null {
    const v = this.refProductCtrl.value;
    return v && typeof v === 'object' ? v as ReturnProductResponse : null;
  }

  get canAddRefItem(): boolean {
    return !!this.selectedRefProduct && !!this.refQtyCtrl.value && (this.refQtyCtrl.value ?? 0) > 0;
  }

  displayRefProduct = (p: ReturnProductResponse | null): string => p?.name ?? '';

  addRefItem(): void {
    const p = this.selectedRefProduct;
    const qty = this.refQtyCtrl.value;
    if (!p || !qty || qty <= 0) return;
    this.refLineItems = [...this.refLineItems, { productId: p.id, productName: p.name, quantity: qty, unitPrice: p.unitPrice }];
    this.refProductCtrl.setValue(null, { emitEvent: false });
    this.refQtyCtrl.setValue(null);
    this.filteredRefProducts = [];
    this.cdr.detectChanges();
  }

  removeRefItem(i: number): void {
    this.refLineItems = this.refLineItems.filter((_, idx) => idx !== i);
    this.cdr.detectChanges();
  }

  saveRefItems(): void {
    if (!this.bill || this.refLineItems.length === 0) return;
    this.savingRefItems = true;
    this.refItemSuccess = '';
    this.refItemError = '';
    this.stockService.enterReferenceItems(
      this.bill.id,
      this.refLineItems.map(i => ({ productId: i.productId, quantity: i.quantity }))
    ).subscribe({
      next: () => {
        this.refItemSuccess = 'Reference items saved.';
        this.refLineItems = [];
        this.savingRefItems = false;
        this.loadStockStatus(this.bill!.id);
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.refItemError = err?.error?.message ?? 'Failed to save reference items.';
        this.savingRefItems = false;
        this.cdr.detectChanges();
      },
    });
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
        this.loadStockStatus(id);
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

  private loadStockStatus(billId: number): void {
    this.stockLoading = true;
    this.stockService.getBillStockStatus(billId).subscribe({
      next: (s) => {
        this.stockStatus = s;
        this.stockLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.stockLoading = false;
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

  private loadProducts(): void {
    this.stockService.getRaincoProducts().subscribe({
      next: (p) => { this.allProducts = p; this.cdr.detectChanges(); },
      error: () => {}
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

  cancelBill(): void {
    if (!this.bill) return;
    if (!confirm(`Cancel bill ${this.bill.billNumber}? The bill will be marked as CANCELLED.`)) return;
    this.billService.cancelBill(this.bill.id).subscribe({
      next: (updated) => {
        this.bill = updated;
        this.cdr.detectChanges();
      },
      error: (err) => alert(err?.error?.message ?? 'Failed to cancel bill.'),
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