import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { WorkerPortalService, WorkerBill, WorkerPaymentEntry, VisitStatus } from '../../../core/services/worker-portal';
import { WorkerTranslateService } from '../../../core/services/worker-translate';
import { BANKS, AREAS } from '../../../core/constants/payment-options';

type Sheet = 'none' | 'delivered_choice' | 'no_payment_reason' | 'visit_not_delivered' | 'payment' | 'edit_payment';

@Component({
  selector: 'app-worker-bill-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, MatIconModule, MatProgressSpinnerModule, DecimalPipe],
  templateUrl: './worker-bill-detail.html',
  styleUrl: './worker-bill-detail.scss',
})
export class WorkerBillDetailComponent implements OnInit {
  @Input() bill!: WorkerBill;
  @Output() close = new EventEmitter<boolean>(); // true = reload

  detail: WorkerBill | null = null;
  loading = false;

  activeSheet: Sheet = 'none';

  readonly banks = BANKS;
  readonly areas = AREAS;

  // Delivery choice (after clicking Delivered)
  deliveryNoPaymentNote = '';
  savingVisit = false;

  // Not-delivered sheet
  notDeliveredReason: VisitStatus | null = null;
  notDeliveredNote = '';

  // Payment
  payAmount: number | null = null;
  payType: 'CASH' | 'CHEQUE' | 'BANK_TRANSFER' = 'CASH';
  payChequeNo = '';
  payChequeDate = '';
  payBank = '';
  payBranch = '';
  payNote = '';
  savingPayment = false;
  payError = '';

  // Edit payment
  editingEntry: WorkerPaymentEntry | null = null;

  // Worker note on bill
  workerNote = '';
  savingNote = false;

  constructor(
    public tr: WorkerTranslateService,
    private svc: WorkerPortalService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.loadDetail();
  }

  loadDetail(): void {
    this.loading = true;
    this.svc.getBillDetail(this.bill.id).subscribe({
      next: d => {
        this.detail = d;
        this.payAmount = d.tempBalance > 0 ? d.tempBalance : null;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => { this.loading = false; this.cdr.detectChanges(); },
    });
  }

  get b(): WorkerBill { return this.detail ?? this.bill; }
  get isFullyCollected(): boolean { return (this.b.tempBalance ?? 0) <= 0; }
  get isCollectionOnly(): boolean { return !!this.b.collectionOnly; }
  get isDelivered(): boolean {
    return this.b.visitStatus === 'DELIVERED_PENDING' || this.b.visitStatus === 'DELIVERED_PAID';
  }

  // ── Delivery actions ─────────────────────────────────────────────

  openDeliveredChoice(): void {
    this.deliveryNoPaymentNote = '';
    this.activeSheet = 'delivered_choice';
    this.cdr.detectChanges();
  }

  collectNow(): void {
    // Mark delivered then open payment sheet
    this.savingVisit = true;
    this.svc.markVisit(this.b.id, 'DELIVERED_PENDING').subscribe({
      next: () => {
        this.savingVisit = false;
        this.activeSheet = 'none';
        this.loadDetail();
        // Small delay so detail reloads before opening payment
        setTimeout(() => { this.openPayment(); this.cdr.detectChanges(); }, 300);
      },
      error: () => { this.savingVisit = false; this.cdr.detectChanges(); },
    });
  }

  confirmDeliveryNoPayment(): void {
    if (!this.deliveryNoPaymentNote.trim()) return;
    this.savingVisit = true;
    this.svc.markVisit(this.b.id, 'DELIVERED_PENDING', this.deliveryNoPaymentNote).subscribe({
      next: () => {
        this.savingVisit = false;
        this.activeSheet = 'none';
        this.loadDetail();
      },
      error: () => { this.savingVisit = false; this.cdr.detectChanges(); },
    });
  }

  openNotDelivered(): void {
    this.notDeliveredReason = null;
    this.notDeliveredNote = '';
    this.activeSheet = 'visit_not_delivered';
    this.cdr.detectChanges();
  }

  confirmNotDelivered(): void {
    if (!this.notDeliveredReason) return;
    if (this.notDeliveredReason === 'REVISIT_REQUESTED' && !this.notDeliveredNote.trim()) return;

    this.savingVisit = true;
    this.svc.markVisit(this.b.id, this.notDeliveredReason, this.notDeliveredNote || undefined).subscribe({
      next: () => {
        this.savingVisit = false;
        this.activeSheet = 'none';
        this.loadDetail();
      },
      error: () => { this.savingVisit = false; this.cdr.detectChanges(); },
    });
  }

  // ── Payment actions ──────────────────────────────────────────────

  openPayment(): void {
    this.payAmount = this.b.tempBalance > 0 ? this.b.tempBalance : null;
    this.payType = 'CASH';
    this.payChequeNo = '';
    this.payChequeDate = '';
    this.payBank = '';
    this.payBranch = '';
    this.payNote = '';
    this.payError = '';
    this.editingEntry = null;
    this.activeSheet = 'payment';
    this.cdr.detectChanges();
  }

  openEditPayment(entry: WorkerPaymentEntry): void {
    this.editingEntry = entry;
    this.payAmount = entry.amount;
    this.payType = entry.paymentType as 'CASH' | 'CHEQUE' | 'BANK_TRANSFER';
    this.payChequeNo = entry.chequeNumber ?? '';
    this.payBank = entry.bankName ?? '';
    this.payBranch = entry.branchName ?? '';
    this.payNote = entry.workerNote ?? '';
    this.payError = '';
    this.activeSheet = 'payment';
    this.cdr.detectChanges();
  }

  submitPayment(): void {
    if (!this.payAmount || this.payAmount <= 0) { this.payError = 'Enter an amount'; return; }
    if (this.payType === 'CHEQUE' && !this.payChequeNo.trim()) { this.payError = 'Cheque number required'; return; }
    if (this.payType === 'BANK_TRANSFER' && !this.payChequeNo.trim()) { this.payError = 'Reference number required'; return; }

    this.savingPayment = true;
    this.payError = '';

    const req = {
      billId: this.b.id,
      amount: this.payAmount,
      paymentType: this.payType,
      chequeNumber: this.payChequeNo || undefined,
      chequeDate: this.payChequeDate || undefined,
      bankName: this.payBank || undefined,
      branchName: this.payBranch || undefined,
      workerNote: this.payNote || undefined,
    };

    const call = this.editingEntry
      ? this.svc.editPayment(this.editingEntry.id, req)
      : this.svc.enterPayment(req);

    call.subscribe({
      next: () => {
        this.savingPayment = false;
        this.activeSheet = 'none';
        this.loadDetail();
      },
      error: (err) => {
        this.payError = err?.error?.message ?? 'Failed to save payment';
        this.savingPayment = false;
        this.cdr.detectChanges();
      },
    });
  }

  deletePayment(entry: WorkerPaymentEntry): void {
    if (!confirm('Delete this payment entry?')) return;
    this.svc.deletePayment(entry.id).subscribe({
      next: () => this.loadDetail(),
      error: () => {},
    });
  }

  closeSheet(): void {
    this.activeSheet = 'none';
    this.cdr.detectChanges();
  }

  goBack(): void {
    this.close.emit(true);
  }

  visitStatusLabel(s: VisitStatus): string {
    const map: Record<VisitStatus, string> = {
      NOT_VISITED: 'status_not_visited', SHOP_CLOSED: 'status_shop_closed',
      REVISIT_REQUESTED: 'status_revisit', DELIVERED_PENDING: 'status_delivered_pending',
      DELIVERED_PAID: 'status_delivered_paid',
    };
    return this.tr.t(map[s]);
  }
}
