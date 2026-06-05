import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ConfirmDialog } from '../../../shared/confirm-dialog/confirm-dialog';
import { ApproveReturnRequest, BillReturnResponse, BillReturnService, ReceivedItemDto } from '../../../core/services/bill-return';

interface ReceivedQty { [itemId: number]: number; }

@Component({
  selector: 'app-review-returns',
  standalone: true,
  imports: [
    CommonModule, FormsModule, DatePipe, DecimalPipe,
    MatButtonModule, MatIconModule, MatProgressSpinnerModule,
    MatSelectModule, MatFormFieldModule, MatInputModule,
    MatDialogModule, MatSnackBarModule,
  ],
  templateUrl: './review-returns.html',
  styleUrl: './review-returns.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReviewReturns implements OnInit {
  returns: BillReturnResponse[] = [];
  loading = true;
  error = false;

  filterStatus = '';
  filterType = '';
  expandedId: number | null = null;

  receivedQtyMap: { [returnId: number]: ReceivedQty } = {};

  get displayed(): BillReturnResponse[] {
    return this.returns.filter(r => {
      if (this.filterStatus && r.status !== this.filterStatus) return false;
      if (this.filterType && r.returnType !== this.filterType) return false;
      return true;
    });
  }

  get pendingCount(): number {
    return this.returns.filter(r => r.status === 'PENDING').length;
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
      if (r.status === 'PENDING') {
        this.receivedQtyMap[r.id] = {};
        r.items.forEach(item => {
          // Default to requested qty — reviewer reduces if some items are missing
          this.receivedQtyMap[r.id][item.id] = item.quantityRequested ?? 0;
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

  calcReceivedAmount(ret: BillReturnResponse): number {
    const qtyMap = this.receivedQtyMap[ret.id] ?? {};
    let total = ret.items.reduce((sum, item) => {
      const qty = qtyMap[item.id] ?? 0;
      return sum + (item.unitPrice * qty);
    }, 0);
    if (ret.discountPercentage) total -= (total * ret.discountPercentage) / 100;
    if (ret.discountFixed) total -= ret.discountFixed;
    return Math.max(0, total);
  }

  calcShortfall(ret: BillReturnResponse): number {
    const qtyMap = this.receivedQtyMap[ret.id] ?? {};
    return ret.items.reduce((sum, item) => {
      const diff = item.quantityRequested - (qtyMap[item.id] ?? 0);
      return sum + (diff > 0 ? item.unitPrice * diff : 0);
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
}
