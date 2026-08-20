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
import { BillReturnResponse, BillReturnService, BillReturnSummary }
  from '../../../core/services/bill-return';
import { WorkerPortalService, WorkerPaymentEntry, CollectionNote } from '../../../core/services/worker-portal';
import { RequestEditDialog } from '../../../shared/request-edit-dialog/request-edit-dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ConfirmDialog } from '../../../shared/confirm-dialog/confirm-dialog';
import { CustomerHealthDialog }
  from '../../../shared/customer-health-dialog/customer-health-dialog';
import { BillStockStatus, ReturnProductResponse, StockService } from '../../../core/services/stock';
import { ChequeAgeBand, chequeAgeBand, chequeAgeDays, chequeAgeLabel, chequeAgeTooltip } from '../../../core/utils/cheque-age';

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
  /** Server-side working, so the panel never re-derives the discount rules. */
  returnSummary: BillReturnSummary | null = null;
  workers: WorkerResponse[] = [];
  stockStatus: BillStockStatus | null = null;
  pendingWorkerEntries: WorkerPaymentEntry[] = [];
  collectionNotes: CollectionNote[] = [];
  loading = true;
  paymentsLoading = true;
  returnsLoading = false;
  stockLoading = false;
  error = false;

  paymentColumns = ['paymentDate', 'amount', 'type', 'chequeAge', 'status', 'enteredBy', 'actions'];

  /** Days from bill date to cheque date — how long the customer is taking to pay. */
  ageDays(p: PaymentResponse): number | null {
    if (p.paymentType !== 'CHEQUE') return null;
    return chequeAgeDays(this.bill?.billDate ?? p.billDate, p.chequeDate);
  }

  ageBand(days: number): ChequeAgeBand { return chequeAgeBand(days); }
  ageLabel(days: number): string { return chequeAgeLabel(days); }
  ageTooltip(days: number): string { return chequeAgeTooltip(days); }
  stockItemColumns = ['productName', 'quantity', 'unitPrice', 'lineTotal'];
  comparisonColumns = ['productName', 'systemQty', 'childQty', 'diff'];
  returnItemColumns = ['itemName', 'quantityRequested', 'quantityReturned', 'unitPrice', 'lineTotal'];

  // Reference item entry (for SYSTEM linking bills)
  allProducts: ReturnProductResponse[] = [];
  filteredRefProducts: ReturnProductResponse[] = [];
  refProductCtrl = new FormControl<ReturnProductResponse | string | null>(null);
  refQtyCtrl = new FormControl<number | null>(null);
  refLineItems: { productId: number; productName: string; quantity: number; unitPrice: number }[] = [];
  savingRefItems = false;
  refItemSuccess = '';
  refItemError = '';
  reconcilingStock = false;
  reconcileError = '';

  get approvedDamageTotal(): number {
    if (this.returnSummary) return this.returnSummary.damageTotal;
    return this.returns
      .filter(r => r.returnType === 'DAMAGE' && r.status === 'APPROVED')
      .reduce((sum, r) => sum + (r.approvedAmount ?? r.calculatedReturnAmount), 0);
  }

  get approvedSalableTotal(): number {
    if (this.returnSummary) return this.returnSummary.salableTotal;
    return this.returns
      .filter(r => r.returnType === 'SALABLE' && r.status === 'APPROVED')
      .reduce((sum, r) => sum + (r.approvedAmount ?? r.calculatedReturnAmount), 0);
  }

  /**
   * What the bill was invoiced for. Returns no longer reduce this figure - they
   * accumulate separately - so it is read straight off the bill rather than
   * reconstructed by adding the returns back on.
   */
  get originalBillAmount(): number {
    if (this.returnSummary) return this.returnSummary.billTotal;
    return this.bill ? (this.bill.totalAmount as unknown as number) : 0;
  }

  /** Invoiced less returns: what the customer actually owes. */
  get payableAmount(): number {
    if (this.returnSummary) return this.returnSummary.payable;
    return this.originalBillAmount - this.approvedDamageTotal - this.approvedSalableTotal;
  }

  /** Returns nobody has confirmed or reviewed - these block payment on this bill. */
  get openReturns(): BillReturnResponse[] {
    return this.returns.filter(r => r.status === 'PENDING' || r.status === 'GOODS_CONFIRMED');
  }

  get awaitingGoods(): BillReturnResponse[] {
    return this.returns.filter(r => r.status === 'PENDING');
  }

  /**
   * Hides this bill from the aging report, or puts it back. Admin only, and a reason
   * is required to hide: the balance stays owed, so somebody will eventually ask why
   * it is missing from the report.
   */
  toggleAgingVisibility(): void {
    if (!this.bill) return;
    const hiding = !this.bill.excludedFromAging;

    this.dialog.open(ConfirmDialog, {
      data: {
        title: hiding ? 'Hide from Aging Report' : 'Show on Aging Report',
        message: hiding
          ? 'This bill stops appearing on the aging report. The balance stays owed and '
            + 'nothing is deleted — it simply stops being counted as chaseable debt.'
          : 'This bill goes back onto the aging report and counts as chaseable again.',
        confirmText: hiding ? 'Hide from report' : 'Show on report',
        confirmColor: hiding ? 'warn' : 'primary',
        showInput: hiding,
        inputLabel: hiding ? 'Reason (required)' : undefined,
      },
    }).afterClosed().subscribe(result => {
      if (!result?.confirmed) return;
      if (hiding && !String(result.inputValue ?? '').trim()) {
        this.snackBar.open('A reason is needed to hide a bill from the report.',
                           'OK', { duration: 4000 });
        return;
      }
      this.billService.setAgingVisibility(this.bill!.id, hiding, result.inputValue ?? '')
        .subscribe({
          next: (updated) => {
            this.bill = updated;
            this.snackBar.open(
              hiding ? 'Hidden from the aging report.' : 'Back on the aging report.',
              'OK', { duration: 3000 });
            this.cdr.markForCheck();
          },
          error: (err) => this.snackBar.open(
            err?.error?.message ?? 'Could not update.', 'OK', { duration: 5000 }),
        });
    });
  }

  returnStatusLabel(status: string): string {
    switch (status) {
      case 'PENDING':         return 'Awaiting goods';
      case 'GOODS_CONFIRMED': return 'Awaiting review';
      case 'APPROVED':        return 'Approved';
      case 'REJECTED':        return 'Rejected';
      case 'NOT_RECEIVED':    return 'Not received';
      case 'CANCELLED':       return 'Cancelled';
      default:                return status;
    }
  }

  /**
   * The accountant recording what physically came back. Until this is answered the
   * bill cannot take a payment, which is what stops a return being missed.
   */
  confirmGoods(r: BillReturnResponse, receipt: 'ALL' | 'PARTIAL' | 'NONE'): void {
    const needsNote = receipt !== 'ALL';
    this.dialog.open(ConfirmDialog, {
      data: {
        title: receipt === 'ALL' ? 'Confirm All Received'
             : receipt === 'NONE' ? 'Confirm Nothing Received'
             : 'Confirm Partly Received',
        message: receipt === 'ALL'
          ? `Everything claimed on this ${r.returnType.toLowerCase()} return came back?`
          : 'Note what is missing so the admin can chase it. '
            + 'Only what actually arrived will come off the bill.',
        confirmText: 'Confirm',
        confirmColor: 'primary',
        showInput: needsNote,
        inputLabel: needsNote ? 'What was missing' : undefined,
      },
    }).afterClosed().subscribe(result => {
      if (!result?.confirmed) return;
      this.billReturnService.confirmGoods(r.id, {
        receipt,
        note: result.inputValue ?? '',
      }).subscribe({
        next: () => {
          this.snackBar.open('Goods confirmed. The admin can now review this return.',
                             'OK', { duration: 4000 });
          if (this.bill) this.reloadBillAndReturns(this.bill.id);
        },
        error: (err) => this.snackBar.open(
          err?.error?.message ?? 'Failed to confirm.', 'OK', { duration: 6000 }),
      });
    });
  }

  get isAccountant(): boolean { return this.auth.getRole() === 'ACCOUNTANT'; }
  get isOwner(): boolean { return this.auth.getRole() === 'OWNER'; }
  get isMainAccountant(): boolean { return this.auth.getRole() === 'MAIN_ACCOUNTANT'; }
  get isAdmin(): boolean { return this.auth.getRole() === 'ADMIN'; }
  get isCancellable(): boolean {
    return this.bill?.status !== 'CANCELLED' && this.bill?.status !== 'COMPLETED';
  }

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
      this.bill?.status !== 'COMPLETED' &&
      this.pendingWorkerEntries.length === 0;
  }

  get hasPendingWorkerEntry(): boolean {
    return this.pendingWorkerEntries.length > 0;
  }

  get pendingCollectionNote(): CollectionNote | null {
    return this.collectionNotes.find(n => n.status === 'PENDING') ?? null;
  }

  get canRequestEdit(): boolean {
    return this.isAccountant || this.isMainAccountant;
  }

  get canMarkReconciled(): boolean {
    return this.isAdmin &&
      !!this.stockStatus?.linkedChildren?.length &&
      !this.stockStatus?.stockReconciled;
  }

  get canToggleCollectionOnly(): boolean {
    return this.isAdmin || this.isAccountant || this.isMainAccountant;
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
    private workerPortalService: WorkerPortalService,
    private cdr: ChangeDetectorRef,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.load(id);
    this.loadWorkers();
    this.loadProducts();
    this.loadWorkerCollectionStatus(id);

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
        this.loadSettledBy();
        this.loadSettlementLinks();
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
    // The summary carries the server's own working, so the panel shows the same
    // damage and salable figures the balance was computed from.
    this.billReturnService.getSummary(billId).subscribe({
      next: (sum) => {
        this.returnSummary = sum;
        this.returns = sum.returns;
        this.returnsLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        // Fall back to the plain list rather than showing nothing.
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
      },
    });
  }

  /** After a confirmation the balance may have moved, so refetch both. */
  private reloadBillAndReturns(billId: number): void {
    this.load(billId);
    this.loadReturns(billId);
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
      next: (w) => this.workers = w.filter(w => w.active && w.billAssignable),
      error: () => this.workers = []
    });
  }

  loadWorkerCollectionStatus(billId: number): void {
    this.workerPortalService.getPendingEntriesForBill(billId).subscribe({
      next: (entries) => { this.pendingWorkerEntries = entries; this.cdr.detectChanges(); },
      error: () => {}
    });
    this.workerPortalService.getCollectionNotesForBill(billId).subscribe({
      next: (notes) => { this.collectionNotes = notes; this.cdr.detectChanges(); },
      error: () => {}
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
    const note = this.pendingCollectionNote;
    this.router.navigate(['/payments/enter'], {
      state: {
        preselectedBill: this.bill,
        ...(note ? {
          collectionNoteId: note.id,
          prefillAmount: note.amount,
          prefillPaymentType: note.paymentType,
          prefillChequeNumber: note.chequeNumber,
          prefillBankName: note.bankName,
          prefillBranchName: note.branchName,
        } : {}),
      }
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

  markStockReconciled(): void {
    if (!this.bill) return;
    const qty = this.stockStatus?.quantitiesMatch;
    const msg = qty
      ? `Mark ${this.bill.billNumber} as RECONCILED?\n\nAll quantities match. This confirms the stock reconciliation is complete.`
      : `Mark ${this.bill.billNumber} as RECONCILED?\n\n⚠ Quantities do not fully match. You can still reconcile, but please verify the discrepancy.`;
    if (!confirm(msg)) return;
    this.reconcilingStock = true;
    this.reconcileError = '';
    this.stockService.reconcileBill(this.bill.id).subscribe({
      next: () => {
        this.reconcilingStock = false;
        this.loadStockStatus(this.bill!.id);
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.reconcileError = err?.error?.message ?? 'Failed to mark as reconciled.';
        this.reconcilingStock = false;
        this.cdr.detectChanges();
      },
    });
  }

  cancelBill(): void {
    if (!this.bill) return;
    const billNum = this.bill.billNumber;
    const billId = this.bill.id;
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Cancel Bill',
        message: `Cancel bill ${billNum}?

It keeps its number and stays in the run, so `
               + 'the reason is all anyone finding it later will have.\n\n'
               + 'If the sale is real and was billed by hand instead, link it to that '
               + 'bill rather than cancelling — cancelling says the sale never happened.',
        confirmText: 'Cancel Bill',
        confirmColor: 'warn',
        showInput: true,
        inputLabel: 'Reason (required)',
      },
      maxWidth: '95vw',
    }).afterClosed().subscribe(result => {
      if (!result?.confirmed) return;

      const reason = String(result.inputValue ?? '').trim();
      // Checked here as well as on the server: being told after the dialog closed
      // means typing the reason into a box that is no longer on screen.
      if (!reason) {
        this.snackBar.open('A reason is needed to cancel a bill.', 'OK', { duration: 4000 });
        return;
      }

      this.billService.cancelBill(billId, reason).subscribe({
        next: (updated) => {
          this.bill = updated;
          this.snackBar.open('Bill cancelled.', 'OK', { duration: 4000 });
          this.cdr.detectChanges();
        },
        error: (err) => this.snackBar.open(
          err?.error?.message ?? 'Failed to cancel bill.', 'OK', { duration: 6000 }),
      });
    });
  }

  // ── Collected on another bill ──────────────────────────────────────────────
  // The same sale gets billed twice: by hand at the shop, and here to keep the record
  // and move the stock. Only one is collected. This says which, so the other stops
  // being chased without pretending it never happened.

  /** Manual bills this one could be collected on. Loaded only when the picker opens. */
  settleCandidates: BillResponse[] = [];
  /** Bills collected on this one — the banner shown on the manual bill's own page. */
  settledByBills: BillResponse[] = [];
  settlePickerOpen = false;
  loadingCandidates = false;
  settleNote = '';

  /**
   * Every hand-written bill collecting this bill's money.
   *
   * <p>Usually several: a rep on a round writes a bill at each shop while the system
   * raises one covering the load.
   */
  settlementLinks: BillResponse[] = [];

  /** Ticked in the picker, linked together when Link is pressed. */
  picked = new Set<number>();

  togglePick(id: number): void {
    if (this.picked.has(id)) this.picked.delete(id);
    else this.picked.add(id);
    this.cdr.markForCheck();
  }

  loadSettlementLinks(): void {
    if (!this.bill) return;
    this.billService.getSettlementLinks(this.bill.id).subscribe({
      next: (list) => { this.settlementLinks = list; this.cdr.markForCheck(); },
      error: () => { this.settlementLinks = []; this.cdr.markForCheck(); },
    });
  }

  /** What the linked bills add up to — checked against this bill's own total. */
  get linkedTotal(): number {
    return this.settlementLinks.reduce((sum, b) => sum + (b.totalAmount ?? 0), 0);
  }

  /**
   * The gap between this bill and what is collecting for it.
   *
   * <p>Shown rather than enforced: the totals genuinely differ when a load is billed at
   * one price and the shops at another, and refusing the link would stop a true record
   * being made. But a large gap usually means a bill is missing from the set.
   */
  get linkedVariance(): number {
    return (this.bill?.totalAmount ?? 0) - this.linkedTotal;
  }
  /** Typed to find the number that was written by hand. */
  settleSearch = '';

  /** How many rows the picker will ever show at once. */
  private readonly SETTLE_ROWS = 3;

  /**
   * Every unlinked manual bill of this business, narrowed by what has been typed.
   *
   * <p>The server cannot narrow by customer — the hand-written bill and the system copy
   * spell the name differently — so the whole list arrives and is filtered here on
   * number or customer, whichever the admin recalls.
   */
  get matchingCandidates(): BillResponse[] {
    const q = this.settleSearch.trim().toLowerCase();
    if (!q) return this.settleCandidates;
    return this.settleCandidates.filter(c =>
      c.billNumber.toLowerCase().includes(q) ||
      (c.customerName ?? '').toLowerCase().includes(q));
  }

  /**
   * Three rows, never more.
   *
   * <p>A business has hundreds of manual bills and listing them ran the page off the
   * bottom of the screen. Three keeps the picker the size of the decision being made:
   * the admin already knows the number they wrote, so this is a confirmation, not a
   * browse. If the right one is not among them, typing another character is faster than
   * scrolling ever was.
   */
  get visibleCandidates(): BillResponse[] {
    return this.matchingCandidates.slice(0, this.SETTLE_ROWS);
  }

  /** How many matches are being held back, so the count is never silently wrong. */
  get hiddenCandidateCount(): number {
    return Math.max(0, this.matchingCandidates.length - this.SETTLE_ROWS);
  }

  /** Only worth offering while there is still a balance nobody has collected. */
  get canLinkSettlement(): boolean {
    return this.isAdmin
        && !!this.bill
        && !this.bill.settledOnBillId
        && this.bill.status !== 'CANCELLED'
        && (this.bill.amountPaid ?? 0) <= 0
        && this.settledByBills.length === 0;
  }

  loadSettledBy(): void {
    if (!this.bill) return;
    this.billService.getSettledByBills(this.bill.id).subscribe({
      next: (list) => { this.settledByBills = list; this.cdr.markForCheck(); },
      error: () => { this.settledByBills = []; this.cdr.markForCheck(); },
    });
  }

  openSettlePicker(): void {
    if (!this.bill) return;
    this.settlePickerOpen = true;
    this.loadingCandidates = true;
    this.settleNote = '';
    this.settleSearch = '';
    this.picked.clear();
    this.billService.getSettleCandidates(this.bill.id).subscribe({
      next: (list) => {
        this.settleCandidates = list;
        this.loadingCandidates = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.settleCandidates = [];
        this.loadingCandidates = false;
        this.snackBar.open(err?.error?.message ?? 'Could not load bills.', 'OK',
                           { duration: 5000 });
        this.cdr.markForCheck();
      },
    });
  }

  closeSettlePicker(): void {
    this.settlePickerOpen = false;
    this.cdr.markForCheck();
  }

  /** Links everything ticked, in one go. */
  linkPicked(): void {
    if (!this.bill || this.picked.size === 0) return;
    const billId = this.bill.id;
    const ids = [...this.picked];
    const names = this.settleCandidates
      .filter(c => this.picked.has(c.id)).map(c => c.billNumber);

    this.dialog.open(ConfirmDialog, {
      data: {
        title: ids.length === 1
          ? 'Collect on ' + names[0]
          : `Collect across ${ids.length} bills`,
        message: `This bill stays exactly as it is — its lines, its stock, its number. `
               + `It stops counting as outstanding and leaves the aging report, and it `
               + `closes once ${ids.length === 1 ? names[0] : 'all of them are'} paid off.`
               + `\n\n${names.join(', ')}`,
        confirmText: ids.length === 1 ? 'Link' : `Link ${ids.length} bills`,
        confirmColor: 'primary',
      },
      maxWidth: '95vw',
    }).afterClosed().subscribe(result => {
      if (!result?.confirmed) return;
      this.billService.linkSettlement(billId, ids, this.settleNote).subscribe({
        next: (updated) => {
          this.bill = updated;
          this.settlePickerOpen = false;
          this.picked.clear();
          this.loadSettlementLinks();
          this.snackBar.open(
            ids.length === 1 ? 'Collected on ' + names[0] + '.'
                             : `Collected across ${ids.length} bills.`,
            'OK', { duration: 4000 });
          this.cdr.detectChanges();
        },
        error: (err) => this.snackBar.open(
          err?.error?.message ?? 'Could not link the bills.', 'OK', { duration: 6000 }),
      });
    });
  }

  /** Drops one bill from the set, leaving the rest linked. */
  unlinkOne(target: BillResponse): void {
    if (!this.bill) return;
    const billId = this.bill.id;
    this.billService.unlinkOne(billId, target.id).subscribe({
      next: (updated) => {
        this.bill = updated;
        this.loadSettlementLinks();
        this.snackBar.open(target.billNumber + ' removed.', 'OK', { duration: 3000 });
        this.cdr.detectChanges();
      },
      error: (err) => this.snackBar.open(
        err?.error?.message ?? 'Could not remove it.', 'OK', { duration: 6000 }),
    });
  }

  unlinkSettlement(): void {
    if (!this.bill?.settledOnBillId) return;
    const billId = this.bill.id;
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Remove the link',
        message: 'This bill goes back to being chased on its own, and its balance '
               + 'counts as outstanding again.',
        confirmText: 'Remove link',
        confirmColor: 'warn',
      },
    }).afterClosed().subscribe(result => {
      if (!result?.confirmed) return;
      this.billService.unlinkSettlement(billId).subscribe({
        next: (updated) => {
          this.bill = updated;
          this.settlementLinks = [];
          this.snackBar.open('Link removed.', 'OK', { duration: 3000 });
          this.cdr.detectChanges();
        },
        error: (err) => this.snackBar.open(
          err?.error?.message ?? 'Could not remove the link.', 'OK', { duration: 6000 }),
      });
    });
  }

  openBill(id: number): void {
    this.router.navigate(['/bills', id]);
  }

  /** This customer's payment record, business by business. */
  openCustomerHealth(): void {
    if (!this.bill?.customerId) return;
    this.dialog.open(CustomerHealthDialog, {
      data: { customerId: this.bill.customerId, customerName: this.bill.customerName },
      width: '760px',
      maxWidth: '95vw',
    });
  }

  toggleCollectionOnly(): void {
    if (!this.bill) return;
    this.billService.toggleCollectionOnly(this.bill.id).subscribe({
      next: () => {
        this.bill = { ...this.bill!, collectionOnly: !this.bill!.collectionOnly };
        this.cdr.detectChanges();
      },
      error: (err) => alert(err?.error?.message ?? 'Failed to update collection status.'),
    });
  }

  deleteBill(): void {
    if (!this.bill) return;
    const billNum = this.bill.billNumber;
    const billId = this.bill.id;
    const returnWarning = this.returns.length > 0
      ? `\n\n⚠️ This bill has ${this.returns.length} return record${this.returns.length !== 1 ? 's' : ''} which will also be deleted.`
      : '';
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Delete Bill',
        message: `Delete bill ${billNum}? This cannot be undone.\nBills with confirmed payments cannot be deleted.${returnWarning}`,
        confirmText: 'Delete',
        confirmColor: 'warn',
      },
      maxWidth: '95vw',
    }).afterClosed().subscribe(result => {
      if (!result?.confirmed) return;
      this.billService.deleteBill(billId).subscribe({
        next: () => this.router.navigate(['/bills']),
        error: (err) => alert(err?.error?.message ?? 'Failed to delete bill.'),
      });
    });
  }

  deletePayment(payment: PaymentResponse): void {
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Delete Payment',
        message: `Delete payment of Rs ${payment.paymentAmount} on ${payment.paymentDate}? This cannot be undone.`,
        confirmText: 'Delete',
        confirmColor: 'warn',
      },
      maxWidth: '95vw',
    }).afterClosed().subscribe(result => {
      if (!result?.confirmed) return;
      this.paymentService.deletePayment(payment.id).subscribe({
        next: () => this.loadPayments(this.bill!.id),
        error: (err) => alert(err?.error?.message ?? 'Failed to delete payment.'),
      });
    });
  }

  goBack(): void {
    this.router.navigate(['/bills']);
  }
}