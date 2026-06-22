import { Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog } from '@angular/material/dialog';
import { MatDialogModule } from '@angular/material/dialog';
import { RouterLink } from '@angular/router';
import { StockService, StockReductionStatus, BillStockItem, SummaryItemInfo } from '../../../core/services/stock';
import { Bill } from '../../../core/services/bill';
import { Auth } from '../../../core/services/auth';
import { ConfirmDialog } from '../../../shared/confirm-dialog/confirm-dialog';

@Component({
  selector: 'app-stock-reduction-status',
  standalone: true,
  imports: [
    CommonModule, FormsModule, DecimalPipe,
    MatTableModule, MatButtonModule, MatCardModule,
    MatIconModule, MatInputModule, MatFormFieldModule,
    MatSelectModule, MatTooltipModule, MatProgressSpinnerModule,
    MatDialogModule, RouterLink,
  ],
  templateUrl: './stock-reduction-status.html',
  styleUrl: './stock-reduction-status.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StockReductionStatusComponent implements OnInit {
  allRecords: StockReductionStatus[] = [];
  filtered: StockReductionStatus[] = [];
  loading = false;
  showAll = false;

  filterSource = 'ALL';
  filterStatus = 'ALL';
  searchText = '';
  showLinkingSummary = false;

  readonly SUMMARY_STATUSES = ['SUMMARY_PENDING', 'SUMMARY_APPROVED'];
  readonly SUMMARY_LIMIT = 5;
  readonly INDIVIDUAL_LIMIT = 10;

  columns = ['billNumber', 'billSource', 'customerName', 'amount', 'billDate', 'reductionStatus', 'summaryId', 'enteredByName', 'actions'];
  linkingColumns = ['billNumber', 'customerName', 'amount', 'childrenTotal', 'savings', 'reductionStatus'];

  get displayed(): StockReductionStatus[] {
    if (this.showAll) return this.filtered;
    const summaryRecs = this.filtered.filter(r => this.SUMMARY_STATUSES.includes(r.reductionStatus)).slice(0, this.SUMMARY_LIMIT);
    const individualRecs = this.filtered.filter(r => !this.SUMMARY_STATUSES.includes(r.reductionStatus)).slice(0, this.INDIVIDUAL_LIMIT);
    const ids = new Set([...summaryRecs.map(r => r.billId), ...individualRecs.map(r => r.billId)]);
    return this.filtered.filter(r => ids.has(r.billId));
  }

  get hiddenCount(): number {
    return this.showAll ? 0 : Math.max(0, this.filtered.length - this.displayed.length);
  }

  // Expand / item edit state
  expandedBillId: number | null = null;
  billItemsMap = new Map<number, BillStockItem[]>();
  summaryItemsMap = new Map<number, SummaryItemInfo[]>();
  loadingItems = false;
  editingItemId: number | null = null;
  editingQty: number | null = null;

  // Admin: add item to an approved (INDIVIDUALLY_REDUCED) bill
  addingItemToBillId: number | null = null;
  addItemProductId: number | null = null;
  addItemQty: number = 1;
  products: any[] = [];

  summary = { total: 0, notReduced: 0, summaryPending: 0, summaryApproved: 0, individual: 0 };

  get isAdmin(): boolean { return this.auth.getRole() === 'ADMIN'; }
  get canViewItems(): boolean {
    const r = this.auth.getRole();
    return r === 'ADMIN' || r === 'OWNER' || r === 'MAIN_ACCOUNTANT';
  }
  get expandedRecord(): StockReductionStatus | null {
    return this.allRecords.find(r => r.billId === this.expandedBillId) ?? null;
  }
  collapsePanel(): void { this.expandedBillId = null; this.cdr.detectChanges(); }

  constructor(
    private stockService: StockService,
    private billService: Bill,
    private dialog: MatDialog,
    public cdr: ChangeDetectorRef,
    public auth: Auth,
  ) {}

  ngOnInit(): void {
    this.load();
    if (this.isAdmin) {
      this.stockService.getRaincoProducts().subscribe({
        next: (p) => { this.products = p; this.cdr.detectChanges(); },
        error: () => {},
      });
    }
  }

  load(): void {
    this.loading = true;
    this.stockService.getStockReductionStatus().subscribe({
      next: records => {
        this.allRecords = records;
        this.computeSummary();
        this.applyFilter();
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: err => {
        console.error(err);
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  private computeSummary(): void {
    this.summary.total = this.allRecords.length;
    this.summary.notReduced = this.allRecords.filter(r => r.reductionStatus === 'NOT_REDUCED').length;
    this.summary.summaryPending = this.allRecords.filter(r => r.reductionStatus === 'SUMMARY_PENDING').length;
    this.summary.summaryApproved = this.allRecords.filter(r => r.reductionStatus === 'SUMMARY_APPROVED').length;
    this.summary.individual = this.allRecords.filter(r => r.reductionStatus === 'INDIVIDUALLY_REDUCED').length;
    (this.summary as any).linked = this.allRecords.filter(r => r.reductionStatus === 'LINKED').length;
  }

  applyFilter(): void {
    this.filtered = this.allRecords.filter(r => {
      const matchSource = this.filterSource === 'ALL' || r.billSource === this.filterSource;
      const matchStatus = this.filterStatus === 'ALL' || r.reductionStatus === this.filterStatus;
      const matchText = !this.searchText ||
        r.billNumber.toLowerCase().includes(this.searchText.toLowerCase()) ||
        r.customerName.toLowerCase().includes(this.searchText.toLowerCase());
      return matchSource && matchStatus && matchText;
    });
    this.cdr.detectChanges();
  }

  get linkingBills(): StockReductionStatus[] {
    return this.allRecords.filter(r =>
      (r.reductionStatus === 'LINKED' || r.reductionStatus === 'RECONCILED') &&
      r.billSource === 'SYSTEM' && r.savingsAmount !== undefined
    );
  }

  get totalSavings(): number {
    return this.linkingBills.reduce((sum, r) => sum + (r.savingsAmount ?? 0), 0);
  }

  statusLabel(s: string): string {
    return ({
      NOT_REDUCED: 'Not Reduced',
      REDUCTION_PENDING: 'Pending Approval',
      SUMMARY_PENDING: 'Summary Pending',
      SUMMARY_APPROVED: 'Summary Approved',
      INDIVIDUALLY_REDUCED: 'Individual',
      LINKED: 'Linked (covered)',
      WILL_LINK: 'Pending Link',
      RECONCILED: 'Reconciled ✓',
    } as any)[s] ?? s;
  }

  isSummaryBill(row: StockReductionStatus): boolean {
    return row.reductionStatus === 'SUMMARY_PENDING' || row.reductionStatus === 'SUMMARY_APPROVED';
  }

  toggleExpand(row: StockReductionStatus): void {
    if (this.expandedBillId === row.billId) {
      this.expandedBillId = null;
      return;
    }
    this.expandedBillId = row.billId;
    this.editingItemId = null;

    if (this.isSummaryBill(row)) {
      if (!this.summaryItemsMap.has(row.billId)) {
        this.loadingItems = true;
        this.stockService.getBillStockStatus(row.billId).subscribe({
          next: status => {
            this.summaryItemsMap.set(row.billId, status.summaryItems ?? []);
            this.loadingItems = false;
            this.cdr.detectChanges();
          },
          error: () => { this.loadingItems = false; this.cdr.detectChanges(); },
        });
      }
    } else {
      if (!this.billItemsMap.has(row.billId)) {
        this.loadingItems = true;
        this.stockService.getBillStockItems(row.billId).subscribe({
          next: items => {
            this.billItemsMap.set(row.billId, items);
            this.loadingItems = false;
            this.cdr.detectChanges();
          },
          error: () => { this.loadingItems = false; this.cdr.detectChanges(); },
        });
      }
    }
    this.cdr.detectChanges();
  }

  itemsFor(billId: number | null): BillStockItem[] {
    if (billId === null) return [];
    return this.billItemsMap.get(billId) ?? [];
  }

  summaryItemsFor(billId: number | null): SummaryItemInfo[] {
    if (billId === null) return [];
    return this.summaryItemsMap.get(billId) ?? [];
  }

  startEdit(item: BillStockItem): void {
    this.editingItemId = item.id;
    this.editingQty = item.quantity;
    this.cdr.detectChanges();
  }

  cancelEdit(): void {
    this.editingItemId = null;
    this.editingQty = null;
    this.cdr.detectChanges();
  }

  saveEdit(item: BillStockItem): void {
    if (!this.editingQty || this.editingQty <= 0) return;
    this.stockService.updateStockItemQuantity(item.id, this.editingQty).subscribe({
      next: updated => {
        const items = this.billItemsMap.get(item.billId) ?? [];
        const idx = items.findIndex(i => i.id === item.id);
        if (idx >= 0) items[idx] = updated;
        this.billItemsMap.set(item.billId, [...items]);
        this.editingItemId = null;
        this.editingQty = null;
        this.cdr.detectChanges();
      },
      error: () => this.cdr.detectChanges(),
    });
  }

  markStockCleared(row: StockReductionStatus): void {
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Mark Stock as Already Reduced',
        message:
          `Bill: ${row.billNumber}\nCustomer: ${row.customerName}\n\n` +
          `Use this only for old bills where stock was physically given out before being entered into the system. ` +
          `The bill will appear as "Individually Reduced" and will no longer show as pending.\n\nThis cannot be undone.`,
        confirmText: 'Mark Stock Reduced',
        confirmColor: 'warn',
      },
      width: '460px',
    }).afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.billService.markStockCleared(row.billId).subscribe({
        next: () => this.load(),
        error: (err) => alert(err?.error?.message ?? 'Failed to mark stock as reduced.'),
      });
    });
  }

  regularItemsFor(billId: number): BillStockItem[] {
    return (this.billItemsMap.get(billId) ?? []).filter(i => !i.backorder);
  }

  backorderItemsFor(billId: number): BillStockItem[] {
    return (this.billItemsMap.get(billId) ?? []).filter(i => i.backorder);
  }

  issueBackorderItem(item: BillStockItem): void {
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Issue Backorder Item',
        message: `Issue ${item.quantity}x ${item.productName} to ${this.expandedRecord?.customerName}?\n\nThis will deduct from current stock and add Rs ${item.lineTotal?.toLocaleString()} to the bill total.`,
        confirmText: 'Issue Stock',
        confirmColor: 'primary',
      },
      width: '420px',
    }).afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.stockService.issueBackorderItem(item.id).subscribe({
        next: () => {
          this.billItemsMap.delete(this.expandedBillId!);
          this.load();
        },
        error: (err) => alert(err?.error?.message ?? 'Failed to issue backorder item.'),
      });
    });
  }

  toggleAddItemPanel(billId: number): void {
    this.addingItemToBillId = this.addingItemToBillId === billId ? null : billId;
    this.addItemProductId = null;
    this.addItemQty = 1;
    this.cdr.detectChanges();
  }

  submitAddItem(billId: number): void {
    if (!this.addItemProductId || this.addItemQty < 1) return;
    this.stockService.addItemToApprovedBill(billId, this.addItemProductId, this.addItemQty).subscribe({
      next: (newItem) => {
        const items = this.billItemsMap.get(billId) ?? [];
        this.billItemsMap.set(billId, [...items, newItem]);
        this.addingItemToBillId = null;
        this.addItemProductId = null;
        this.addItemQty = 1;
        this.cdr.detectChanges();
      },
      error: (err) => {
        alert(err?.error?.message ?? 'Failed to add item.');
        this.cdr.detectChanges();
      },
    });
  }

  deleteItem(item: BillStockItem): void {
    if (!confirm(`Delete ${item.productName} (qty ${item.quantity})?`)) return;
    this.stockService.deleteStockItem(item.id).subscribe({
      next: () => {
        const items = (this.billItemsMap.get(item.billId) ?? []).filter(i => i.id !== item.id);
        this.billItemsMap.set(item.billId, items);
        // If all items deleted, collapse and refresh list
        if (items.length === 0) {
          this.expandedBillId = null;
          this.load();
        }
        this.cdr.detectChanges();
      },
      error: () => this.cdr.detectChanges(),
    });
  }
}
