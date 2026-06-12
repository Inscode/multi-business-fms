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
import { RouterLink } from '@angular/router';
import { StockService, StockReductionStatus, BillStockItem } from '../../../core/services/stock';
import { Auth } from '../../../core/services/auth';

@Component({
  selector: 'app-stock-reduction-status',
  standalone: true,
  imports: [
    CommonModule, FormsModule, DecimalPipe,
    MatTableModule, MatButtonModule, MatCardModule,
    MatIconModule, MatInputModule, MatFormFieldModule,
    MatSelectModule, MatTooltipModule, MatProgressSpinnerModule, RouterLink,
  ],
  templateUrl: './stock-reduction-status.html',
  styleUrl: './stock-reduction-status.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StockReductionStatusComponent implements OnInit {
  allRecords: StockReductionStatus[] = [];
  filtered: StockReductionStatus[] = [];
  loading = false;

  filterSource = 'ALL';
  filterStatus = 'ALL';
  searchText = '';
  showLinkingSummary = false;

  columns = ['billNumber', 'billSource', 'customerName', 'amount', 'billDate', 'reductionStatus', 'summaryId', 'enteredByName', 'actions'];
  linkingColumns = ['billNumber', 'customerName', 'amount', 'childrenTotal', 'savings', 'reductionStatus'];

  // Expand / item edit state
  expandedBillId: number | null = null;
  billItemsMap = new Map<number, BillStockItem[]>();
  loadingItems = false;
  editingItemId: number | null = null;
  editingQty: number | null = null;

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
    private cdr: ChangeDetectorRef,
    public auth: Auth,
  ) {}

  ngOnInit(): void { this.load(); }

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
      SUMMARY_PENDING: 'Summary Pending',
      SUMMARY_APPROVED: 'Summary Approved',
      INDIVIDUALLY_REDUCED: 'Individual',
      LINKED: 'Linked (covered)',
      WILL_LINK: 'Pending Link',
      RECONCILED: 'Reconciled ✓',
    } as any)[s] ?? s;
  }

  toggleExpand(row: StockReductionStatus): void {
    if (this.expandedBillId === row.billId) {
      this.expandedBillId = null;
      return;
    }
    this.expandedBillId = row.billId;
    this.editingItemId = null;
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
    this.cdr.detectChanges();
  }

  itemsFor(billId: number | null): BillStockItem[] {
    if (billId === null) return [];
    return this.billItemsMap.get(billId) ?? [];
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
