import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { WorkerPortalService, WorkerBill, VisitStatus } from '../../../core/services/worker-portal';
import { WorkerTranslateService } from '../../../core/services/worker-translate';
import { WorkerBillDetailComponent } from '../worker-bill-detail/worker-bill-detail';
import { MatCheckboxModule } from '@angular/material/checkbox';

@Component({
  selector: 'app-worker-bill-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, MatIconModule, MatProgressSpinnerModule, DecimalPipe, WorkerBillDetailComponent, MatCheckboxModule],
  templateUrl: './worker-bill-list.html',
  styleUrl: './worker-bill-list.scss',
})
export class WorkerBillList implements OnInit {
  bills: WorkerBill[] = [];
  filtered: WorkerBill[] = [];
  loading = false;
  error = false;
  searchQuery = '';
  typeFilter: 'all' | 'new' | 'collection' = 'all';
  statusFilter: 'all' | 'not_visited' | 'not_delivered' | 'awaiting' | 'submitted' = 'all';

  selectedBill: WorkerBill | null = null;

  // Combined payment
  multiSelectMode = false;
  selectedBills: WorkerBill[] = [];
  showCombinedSheet = false;
  combinedPayType: 'CASH' | 'CHEQUE' | 'BANK_TRANSFER' = 'CHEQUE';
  combinedChequeNo = '';
  combinedBank = '';
  combinedBranch = '';
  combinedNote = '';
  combinedAmounts: Map<number, number> = new Map();
  savingCombined = false;
  combinedError = '';

  get combinedTotal(): number {
    return Array.from(this.combinedAmounts.values()).reduce((s, v) => s + (v ?? 0), 0);
  }

  constructor(
    public tr: WorkerTranslateService,
    private svc: WorkerPortalService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.error = false;
    this.svc.getBills().subscribe({
      next: bills => {
        this.bills = bills;
        this.applyFilter();
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

  setTypeFilter(t: typeof this.typeFilter): void {
    this.typeFilter = t;
    this.statusFilter = 'all';
    this.applyFilter();
  }

  setStatusFilter(f: typeof this.statusFilter): void {
    this.statusFilter = f;
    this.applyFilter();
  }

  typeCount(t: 'new' | 'collection'): number {
    return this.bills.filter(b => t === 'new' ? this.isNew(b) : !this.isNew(b)).length;
  }

  filterCount(f: typeof this.statusFilter): number {
    const typeFiltered = this.typeFilter === 'all' ? this.bills
      : this.bills.filter(b => this.typeFilter === 'new' ? this.isNew(b) : !this.isNew(b));
    return typeFiltered.filter(b => this.matchesStatusFilter(b, f)).length;
  }

  isNew(b: WorkerBill): boolean {
    if (b.collectionOnly) return false;
    if (!b.createdAt) return false;
    return (Date.now() - new Date(b.createdAt).getTime()) <= 3 * 24 * 60 * 60 * 1000;
  }

  private matchesStatusFilter(b: WorkerBill, f: typeof this.statusFilter): boolean {
    switch (f) {
      case 'not_visited': return b.visitStatus === 'NOT_VISITED';
      case 'not_delivered': return b.visitStatus === 'SHOP_CLOSED' || b.visitStatus === 'REVISIT_REQUESTED';
      case 'awaiting': return b.visitStatus === 'DELIVERED_PENDING' && b.workerPendingTotal === 0;
      case 'submitted': return b.visitStatus === 'DELIVERED_PENDING' && b.workerPendingTotal > 0;
      default: return true;
    }
  }

  applyFilter(): void {
    const q = this.searchQuery.toLowerCase().trim();
    this.filtered = this.bills
      .filter(b => {
        if (this.typeFilter === 'new') return this.isNew(b);
        if (this.typeFilter === 'collection') return !this.isNew(b);
        return true;
      })
      .filter(b => this.matchesStatusFilter(b, this.statusFilter))
      .filter(b => !q || b.customerName.toLowerCase().includes(q) ||
          b.billNumber.toLowerCase().includes(q) ||
          (b.area ?? '').toLowerCase().includes(q));
    this.cdr.detectChanges();
  }

  clearSearch(): void {
    this.searchQuery = '';
    this.applyFilter();
  }

  openBill(bill: WorkerBill): void {
    this.selectedBill = bill;
    this.cdr.detectChanges();
  }

  onDetailClose(refresh: boolean): void {
    this.selectedBill = null;
    if (refresh) this.load();
    this.cdr.detectChanges();
  }

  toggleMultiSelect(): void {
    this.multiSelectMode = !this.multiSelectMode;
    this.selectedBills = [];
    this.cdr.detectChanges();
  }

  toggleBillSelect(bill: WorkerBill, event?: Event): void {
    event?.stopPropagation();
    const idx = this.selectedBills.findIndex(b => b.id === bill.id);
    if (idx >= 0) this.selectedBills.splice(idx, 1);
    else this.selectedBills.push(bill);
    this.cdr.detectChanges();
  }

  isSelected(bill: WorkerBill): boolean {
    return this.selectedBills.some(b => b.id === bill.id);
  }

  openCombinedSheet(): void {
    this.combinedAmounts = new Map(this.selectedBills.map(b => [b.id, b.tempBalance]));
    this.combinedPayType = 'CHEQUE';
    this.combinedChequeNo = '';
    this.combinedBank = '';
    this.combinedBranch = '';
    this.combinedNote = '';
    this.combinedError = '';
    this.showCombinedSheet = true;
    this.cdr.detectChanges();
  }

  closeCombinedSheet(): void {
    this.showCombinedSheet = false;
    this.cdr.detectChanges();
  }

  submitCombined(): void {
    if (this.combinedPayType === 'CHEQUE' && !this.combinedChequeNo.trim()) {
      this.combinedError = 'Cheque number required';
      return;
    }
    if (this.combinedPayType === 'BANK_TRANSFER' && !this.combinedChequeNo.trim()) {
      this.combinedError = 'Reference number required';
      return;
    }
    const bills = this.selectedBills.map(b => ({
      billId: b.id,
      amount: this.combinedAmounts.get(b.id) ?? b.tempBalance,
    }));
    this.savingCombined = true;
    this.combinedError = '';
    this.svc.enterGroupPayment({
      paymentType: this.combinedPayType,
      chequeNumber: this.combinedChequeNo || undefined,
      bankName: this.combinedBank || undefined,
      branchName: this.combinedBranch || undefined,
      workerNote: this.combinedNote || undefined,
      bills,
    }).subscribe({
      next: () => {
        this.savingCombined = false;
        this.showCombinedSheet = false;
        this.multiSelectMode = false;
        this.selectedBills = [];
        this.load();
      },
      error: (err) => {
        this.combinedError = err?.error?.message ?? 'Failed to submit';
        this.savingCombined = false;
        this.cdr.detectChanges();
      },
    });
  }

  billVisitIcon(b: WorkerBill): string {
    if (b.visitStatus === 'DELIVERED_PENDING' && b.workerPendingTotal > 0) return 'pending_actions';
    return ({
      NOT_VISITED: 'radio_button_unchecked',
      SHOP_CLOSED: 'store',
      REVISIT_REQUESTED: 'schedule',
      DELIVERED_PENDING: 'local_shipping',
      DELIVERED_PAID: 'check_circle',
    } as any)[b.visitStatus] ?? 'help';
  }

  billVisitColor(b: WorkerBill): string {
    if (b.visitStatus === 'DELIVERED_PENDING' && b.workerPendingTotal > 0) return '#7b1fa2';
    return ({
      NOT_VISITED: '#bdbdbd',
      SHOP_CLOSED: '#ef5350',
      REVISIT_REQUESTED: '#ff9800',
      DELIVERED_PENDING: '#1976d2',
      DELIVERED_PAID: '#43a047',
    } as any)[b.visitStatus] ?? '#9e9e9e';
  }

  billVisitLabel(b: WorkerBill): string {
    if (b.visitStatus === 'DELIVERED_PENDING' && b.workerPendingTotal > 0) return this.tr.t('status_payment_submitted');
    const map: Record<VisitStatus, string> = {
      NOT_VISITED: 'status_not_visited',
      SHOP_CLOSED: 'status_shop_closed',
      REVISIT_REQUESTED: 'status_revisit',
      DELIVERED_PENDING: 'status_delivered_pending',
      DELIVERED_PAID: 'status_delivered_paid',
    };
    return this.tr.t(map[b.visitStatus]);
  }
}
