import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ConfirmDialog } from '../../../shared/confirm-dialog/confirm-dialog';
import { ApproveReturnRequest, BillReturnResponse, BillReturnService, ReceivedItemDto }
  from '../../../core/services/bill-return';

interface ReceivedQty { [itemId: number]: number; }

@Component({
  selector: 'app-review-returns',
  standalone: true,
  imports: [
    CommonModule, FormsModule, DatePipe, DecimalPipe,
    MatButtonModule, MatIconModule, MatProgressSpinnerModule,
    MatSelectModule, MatFormFieldModule, MatInputModule,
    MatDialogModule, MatSnackBarModule, MatTooltipModule,
  ],
  templateUrl: './review-returns.html',
  styleUrl: './review-returns.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReviewReturns implements OnInit {
  @Input() readonly = false;

  returns: BillReturnResponse[] = [];
  loading = true;
  error = false;

  filterStatus = '';
  filterType = '';
  expandedId: number | null = null;

  receivedQtyMap: { [returnId: number]: ReceivedQty } = {};

  fixingBillAmounts = false;
  fixResult: string | null = null;

  get displayed(): BillReturnResponse[] {
    return this.returns.filter(r => {
      if (this.filterStatus && r.status !== this.filterStatus) return false;
      if (this.filterType && r.returnType !== this.filterType) return false;
      return true;
    });
  }

  /** Everything still owing an action - these are what hold bills open. */
  get pendingCount(): number {
    return this.returns.filter(r => r.status === 'PENDING'
                                 || r.status === 'GOODS_CONFIRMED').length;
  }

  /** Entered but nobody has said whether the goods turned up. */
  get awaitingGoodsCount(): number {
    return this.returns.filter(r => r.status === 'PENDING').length;
  }

  /** Confirmed by the accountant and waiting on the admin. */
  get awaitingReviewCount(): number {
    return this.returns.filter(r => r.status === 'GOODS_CONFIRMED').length;
  }

  isOpen(r: BillReturnResponse): boolean {
    return r.status === 'PENDING' || r.status === 'GOODS_CONFIRMED';
  }

  /** Days since it was entered - a return going stale is a return being forgotten. */
  ageInDays(r: BillReturnResponse): number {
    const ms = Date.now() - new Date(r.submittedAt).getTime();
    return Math.max(0, Math.floor(ms / 86_400_000));
  }

  isStale(r: BillReturnResponse): boolean {
    return this.isOpen(r) && this.ageInDays(r) >= 14;
  }

  statusLabel(status: string): string {
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

  receiptLabel(receipt?: string): string {
    switch (receipt) {
      case 'ALL':     return 'All received';
      case 'PARTIAL': return 'Partly received';
      case 'NONE':    return 'Nothing received';
      default:        return '';
    }
  }

  constructor(
    private billReturnService: BillReturnService,
    private cdr: ChangeDetectorRef,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
  ) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.error = false;
    this.billReturnService.getAll().subscribe({
      next: (r) => {
        this.returns = r;
        this.loading = false;
        this.initQtyMaps(r);
        this.cdr.detectChanges();
      },
      error: () => {
        this.error = true;
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  /** Pre-fill received qty = quantityRequested so reviewer just confirms (or adjusts down) */
  private initQtyMaps(returns: BillReturnResponse[]): void {
    returns.forEach(r => {
      if (r.status === 'PENDING' || r.status === 'GOODS_CONFIRMED') {
        this.receivedQtyMap[r.id] = {};
        r.items.forEach(item => {
          // The accountant's confirmed count is the truth where there is one;
          // otherwise start from what was claimed and let the reviewer cut it down.
          this.receivedQtyMap[r.id][item.id] =
            item.quantityReturned ?? item.quantityRequested ?? 0;
        });
      }
    });
  }

  toggle(id: number): void {
    this.expandedId = this.expandedId === id ? null : id;
    this.cdr.detectChanges();
  }

  getReceivedQty(returnId: number, itemId: number): number {
    return this.receivedQtyMap[returnId]?.[itemId] ?? 0;
  }

  setReceivedQty(returnId: number, itemId: number, value: number): void {
    if (!this.receivedQtyMap[returnId]) this.receivedQtyMap[returnId] = {};
    this.receivedQtyMap[returnId][itemId] = value < 0 ? 0 : value;
    this.cdr.detectChanges();
  }

  /**
   * Credit only what actually came back: each line's credit, prorated by the quantity
   * received. Working from the stored credit rather than re-deriving from unit price
   * keeps this agreeing with the server, which owns the discount rules.
   */
  calcReceivedAmount(ret: BillReturnResponse): number {
    const qtyMap = this.receivedQtyMap[ret.id] ?? {};
    let total = ret.items.reduce((sum, item) => {
      const requested = item.quantityRequested ?? 0;
      const received = qtyMap[item.id] ?? 0;
      const credit = item.creditAmount ?? item.lineTotal ?? 0;
      if (requested <= 0) return sum;
      return sum + (credit * Math.min(received, requested)) / requested;
    }, 0);
    if (ret.discountFixed) total -= ret.discountFixed;
    return Math.max(0, total);
  }

  /** Value claimed but not received - money still owed on the bill. */
  calcShortfall(ret: BillReturnResponse): number {
    const qtyMap = this.receivedQtyMap[ret.id] ?? {};
    return ret.items.reduce((sum, item) => {
      const requested = item.quantityRequested ?? 0;
      const diff = requested - (qtyMap[item.id] ?? 0);
      if (diff <= 0 || requested <= 0) return sum;
      const credit = item.creditAmount ?? item.lineTotal ?? 0;
      return sum + (credit * diff) / requested;
    }, 0);
  }

  buildItems(ret: BillReturnResponse): ReceivedItemDto[] {
    const qtyMap = this.receivedQtyMap[ret.id] ?? {};
    return ret.items.map(item => ({
      id: item.id,
      quantityReturned: qtyMap[item.id] ?? 0,
    }));
  }

  approve(ret: BillReturnResponse, approveWith: string): void {
    const receivedAmount = this.calcReceivedAmount(ret);

    if (approveWith === 'CALCULATED' && receivedAmount <= 0) {
      this.snackBar.open('Enter at least one received quantity before approving.', 'OK', { duration: 4000 });
      return;
    }

    const label = approveWith === 'PREDICTED'
      ? `predicted value (Rs ${ret.predictedValue?.toFixed(2)})`
      : `received amount (Rs ${receivedAmount.toFixed(2)})`;

    const shortfall = this.calcShortfall(ret);
    const shortfallNote = shortfall > 0 ? `\n\nShortfall: Rs ${shortfall.toFixed(2)} will NOT be deducted.` : '';

    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Approve Return',
        message: `Approve return for ${ret.billNumber} using ${label}?\nThis will deduct from the bill total.${shortfallNote}`,
        confirmText: 'Approve',
        confirmColor: 'primary',
      },
    }).afterClosed().subscribe(result => {
      if (!result?.confirmed) return;
      const req: ApproveReturnRequest = { approveWith, items: this.buildItems(ret) };
      this.billReturnService.approve(ret.id, req).subscribe({
        next: () => {
          this.snackBar.open('Return approved. Stock updated.', 'OK', { duration: 3000 });
          this.load();
        },
        error: (err) => {
          this.snackBar.open(err?.error?.message ?? 'Failed to approve.', 'OK', { duration: 5000 });
        },
      });
    });
  }

  reject(ret: BillReturnResponse): void {
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Reject Return',
        message: `Reject this return for ${ret.billNumber}?`,
        confirmText: 'Reject',
        confirmColor: 'warn',
        showInput: true,
        inputLabel: 'Rejection reason (optional)',
      },
    }).afterClosed().subscribe(result => {
      if (!result?.confirmed) return;
      this.billReturnService.reject(ret.id, result.inputValue ?? '').subscribe({
        next: () => {
          this.snackBar.open('Return rejected.', 'OK', { duration: 3000 });
          this.load();
        },
        error: () => this.snackBar.open('Failed to reject.', 'OK', { duration: 4000 }),
      });
    });
  }

  /** The goods were claimed but never turned up - nothing comes off the bill. */
  markNotReceived(ret: BillReturnResponse): void {
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Mark as Not Received',
        message: `Nothing came back for ${ret.billNumber}?\n\n`
               + 'The full bill amount stays payable and the return is closed.',
        confirmText: 'Not received',
        confirmColor: 'warn',
        showInput: true,
        inputLabel: 'What happened (optional)',
      },
    }).afterClosed().subscribe(result => {
      if (!result?.confirmed) return;
      this.billReturnService.markNotReceived(ret.id, result.inputValue ?? '').subscribe({
        next: () => {
          this.snackBar.open('Marked as not received.', 'OK', { duration: 3000 });
          this.load();
        },
        error: (err) => this.snackBar.open(
          err?.error?.message ?? 'Failed to update.', 'OK', { duration: 5000 }),
      });
    });
  }

  /**
   * Reverses a return the accountant should not have entered. The credit goes back
   * onto the bill and the stock movement is undone.
   */
  cancel(ret: BillReturnResponse): void {
    const amount = ret.approvedAmount ?? ret.calculatedReturnAmount ?? 0;
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Cancel Return',
        message: `Put Rs ${amount.toFixed(2)} back onto ${ret.billNumber}?\n\n`
               + 'The return is reversed and any stock it moved is undone. '
               + 'Use this when the return should not have been entered.',
        confirmText: 'Cancel return',
        confirmColor: 'warn',
        showInput: true,
        inputLabel: 'Reason',
      },
    }).afterClosed().subscribe(result => {
      if (!result?.confirmed) return;
      this.billReturnService.cancel(ret.id, result.inputValue ?? '').subscribe({
        next: () => {
          this.snackBar.open('Return cancelled. The bill is back to its full amount.',
                             'OK', { duration: 4000 });
          this.load();
        },
        error: (err) => this.snackBar.open(
          err?.error?.message ?? 'Failed to cancel.', 'OK', { duration: 6000 }),
      });
    });
  }

  fixHistoricalBillAmounts(): void {
    this.fixingBillAmounts = true;
    this.fixResult = null;
    this.billReturnService.fixHistoricalBillAmounts().subscribe({
      next: (r) => {
        this.fixResult = `Fixed ${r.fixed} return${r.fixed !== 1 ? 's' : ''}`;
        this.fixingBillAmounts = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.fixResult = 'Fix failed — check console';
        this.fixingBillAmounts = false;
        this.cdr.detectChanges();
      },
    });
  }
}
