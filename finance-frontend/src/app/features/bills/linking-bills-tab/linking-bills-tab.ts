import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { BillResponse } from '../../../core/services/bill';
import { Bill } from '../../../core/services/bill';
import { BillStockStatus, StockService } from '../../../core/services/stock';
import { ConfirmDialog } from '../../../shared/confirm-dialog/confirm-dialog';

@Component({
  selector: 'app-linking-bills-tab',
  standalone: true,
  imports: [
    CommonModule, DatePipe, DecimalPipe,
    MatButtonModule, MatIconModule, MatProgressSpinnerModule,
    MatDialogModule, MatSnackBarModule, MatTooltipModule,
  ],
  templateUrl: './linking-bills-tab.html',
  styleUrl: './linking-bills-tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LinkingBillsTab implements OnInit {
  @Input() isAdmin = false;

  bills: BillResponse[] = [];
  loading = true;
  error = false;

  expandedBillId: number | null = null;
  expandedStatus: BillStockStatus | null = null;
  expandedLoading = false;

  savingsToggling = false;
  reconciling = false;

  constructor(
    private billService: Bill,
    private stockService: StockService,
    private cdr: ChangeDetectorRef,
    private snackBar: MatSnackBar,
    private dialog: MatDialog,
  ) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.error = false;
    this.billService.getLinkingBills().subscribe({
      next: (b) => {
        this.bills = b;
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

  expand(bill: BillResponse): void {
    if (this.expandedBillId === bill.id) {
      this.expandedBillId = null;
      this.expandedStatus = null;
      this.cdr.detectChanges();
      return;
    }
    this.expandedBillId = bill.id;
    this.expandedStatus = null;
    this.expandedLoading = true;
    this.cdr.detectChanges();
    this.stockService.getBillStockStatus(bill.id).subscribe({
      next: (s) => {
        this.expandedStatus = s;
        this.expandedLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.expandedLoading = false;
        this.snackBar.open('Failed to load bill stock status.', 'OK', { duration: 3000 });
        this.cdr.detectChanges();
      },
    });
  }

  reconcile(): void {
    if (!this.expandedBillId) return;
    const billId = this.expandedBillId;
    const bill = this.bills.find(b => b.id === billId);
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Reconcile Stock',
        message: `Mark stock reconciled for ${bill?.billNumber ?? 'this bill'}?\nThis confirms the stock quantities have been verified.`,
        confirmText: 'Reconcile',
        confirmColor: 'primary',
      },
      width: '400px',
    }).afterClosed().subscribe(result => {
      if (!result?.confirmed) return;
      this.reconciling = true;
      this.stockService.reconcileBill(billId).subscribe({
        next: () => {
          this.reconciling = false;
          this.snackBar.open('Stock reconciled.', 'OK', { duration: 3000 });
          // reload expanded status
          this.stockService.getBillStockStatus(billId).subscribe({
            next: (s) => { this.expandedStatus = s; this.cdr.detectChanges(); },
          });
          this.cdr.detectChanges();
        },
        error: (err) => {
          this.reconciling = false;
          this.snackBar.open(err?.error?.message ?? 'Failed to reconcile.', 'OK', { duration: 4000 });
          this.cdr.detectChanges();
        },
      });
    });
  }

  toggleCollected(): void {
    if (!this.expandedBillId || this.savingsToggling) return;
    const billId = this.expandedBillId;
    const newValue = !this.expandedStatus?.savingsCollected;
    this.savingsToggling = true;
    this.cdr.detectChanges();
    this.stockService.markSavingsCollected(billId, newValue).subscribe({
      next: () => {
        if (this.expandedStatus) this.expandedStatus.savingsCollected = newValue;
        this.savingsToggling = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.savingsToggling = false;
        this.snackBar.open('Failed to update savings collected status.', 'OK', { duration: 3000 });
        this.cdr.detectChanges();
      },
    });
  }

  get savings(): number {
    return this.expandedStatus?.savingsAmount ?? 0;
  }

  get childrenTotal(): number {
    return this.expandedStatus?.childrenTotalAmount ?? 0;
  }

  get quantitiesMatch(): boolean {
    return this.expandedStatus?.quantitiesMatch ?? false;
  }

  get isReconciled(): boolean {
    return this.expandedStatus?.stockReconciled ?? false;
  }

  get isSavingsCollected(): boolean {
    return this.expandedStatus?.savingsCollected ?? false;
  }
}
