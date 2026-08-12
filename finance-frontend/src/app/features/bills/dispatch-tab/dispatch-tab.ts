import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SelectionModel } from '@angular/cdk/collections';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Bill, BillResponse } from '../../../core/services/bill';
import { Worker, WorkerResponse } from '../../../core/services/worker';
import { BulkAssignDialog } from '../bulk-assign-dialog/bulk-assign-dialog';

/**
 * Collection dispatch round: a worker asks for an area's bills, you hand them over
 * by assigning them, and they leave the working list. An empty list means every
 * bill in that area has been given to someone — nothing missed.
 *
 * Bills already assigned stay visible in a separate section (with who holds them),
 * so an older assignment is never invisible during the round.
 */
@Component({
  selector: 'app-dispatch-tab',
  standalone: true,
  imports: [
    CommonModule, FormsModule, DatePipe, DecimalPipe,
    MatButtonModule, MatIconModule, MatSelectModule, MatFormFieldModule, MatInputModule,
    MatCheckboxModule, MatProgressSpinnerModule, MatDialogModule, MatTooltipModule,
  ],
  templateUrl: './dispatch-tab.html',
  styleUrl: './dispatch-tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DispatchTab implements OnInit {
  private allBills: BillResponse[] = [];
  workers: WorkerResponse[] = [];
  areas: string[] = [];

  loading = true;
  error = false;

  selectedBusiness = '';
  selectedAreas: string[] = [];
  search = '';
  billTypeFilter: 'ALL' | 'CASH' | 'CREDIT' = 'ALL';
  showAssigned = false;

  businesses = ['', 'RAINCO', 'RETAIL_SHOP', 'STATIONERY', 'PLASTIC', 'HARDWARE', 'MIX'];

  selection = new SelectionModel<BillResponse>(true, []);

  constructor(
    private billService: Bill,
    private workerService: Worker,
    private dialog: MatDialog,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.loadWorkers();
    this.load();
  }

  // ── data ────────────────────────────────────────────────────────

  load(): void {
    this.loading = true;
    this.error = false;
    this.cdr.markForCheck();

    // Pending only — a settled bill is never part of a collection round
    this.billService.getBills({ excludeCompleted: true }).subscribe({
      next: (bills) => {
        // willBeLinked bills are reference only — their value sits on the child bills,
        // so including them here would double-count the round.
        this.allBills = bills.filter(b =>
          b.balanceRemaining > 0 && b.status !== 'CANCELLED' && !b.willBeLinked);
        this.areas = [...new Set(this.allBills.map(b => b.area).filter((a): a is string => !!a))].sort();
        this.selection.clear();
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: () => { this.error = true; this.loading = false; this.cdr.markForCheck(); },
    });
  }

  private loadWorkers(): void {
    this.workerService.getAllWorkers().subscribe({
      next: (w) => {
        // Only staff who actually carry bills — same rule as the bills list
        this.workers = w.filter(x => x.active && x.billAssignable);
        this.cdr.markForCheck();
      },
      error: () => { this.workers = []; this.cdr.markForCheck(); },
    });
  }

  // ── filtering ───────────────────────────────────────────────────

  /** No areas picked means every area — the round covers the lot. */
  private matchesScope(b: BillResponse): boolean {
    return (!this.selectedBusiness || b.business === this.selectedBusiness)
      && (this.selectedAreas.length === 0 || (!!b.area && this.selectedAreas.includes(b.area)));
  }

  private matchesSearch(b: BillResponse): boolean {
    const q = this.search.toLowerCase().trim();
    if (!q) return true;
    return (b.billNumber ?? '').toLowerCase().includes(q)
        || b.customerName.toLowerCase().includes(q);
  }

  private get inScope(): BillResponse[] {
    return this.allBills.filter(b =>
      this.matchesScope(b) &&
      this.matchesSearch(b) &&
      (this.billTypeFilter === 'ALL' || b.billType === this.billTypeFilter));
  }

  /** Ignores the type filter — used for the type chip counts. */
  private get inArea(): BillResponse[] {
    return this.allBills.filter(b => this.matchesScope(b) && this.matchesSearch(b));
  }

  /** Human-readable area scope for the empty-state message. */
  get areaLabel(): string {
    if (this.selectedAreas.length === 0) return '';
    if (this.selectedAreas.length <= 2) return this.selectedAreas.join(' and ');
    return `${this.selectedAreas.length} areas`;
  }

  get isFiltered(): boolean {
    return !!this.selectedBusiness || this.selectedAreas.length > 0
        || this.billTypeFilter !== 'ALL' || !!this.search.trim();
  }

  onSearchChange(): void {
    this.selection.clear();
    this.cdr.markForCheck();
  }

  typeCount(type: 'ALL' | 'CASH' | 'CREDIT'): number {
    const rows = this.inArea.filter(b => !b.workerId);
    return type === 'ALL' ? rows.length : rows.filter(b => b.billType === type).length;
  }

  setTypeFilter(type: 'ALL' | 'CASH' | 'CREDIT'): void {
    this.billTypeFilter = type;
    this.selection.clear();
    this.cdr.markForCheck();
  }

  isCash(bill: BillResponse): boolean { return bill.billType === 'CASH'; }

  /** The working list — what is still to hand over. */
  get toDispatch(): BillResponse[] {
    return this.inScope.filter(b => !b.workerId);
  }

  /** Already with a worker — kept visible so an older assignment isn't missed. */
  get assigned(): BillResponse[] {
    return this.inScope.filter(b => !!b.workerId);
  }

  get toDispatchTotal(): number {
    return this.toDispatch.reduce((sum, b) => sum + b.balanceRemaining, 0);
  }

  onFilterChange(): void {
    this.selection.clear();
    this.cdr.markForCheck();
  }

  clearFilters(): void {
    this.selectedBusiness = '';
    this.selectedAreas = [];
    this.search = '';
    this.billTypeFilter = 'ALL';
    this.onFilterChange();
  }

  toggleAssignedPanel(): void {
    this.showAssigned = !this.showAssigned;
    this.cdr.markForCheck();
  }

  // ── selection ───────────────────────────────────────────────────

  get allSelected(): boolean {
    const rows = this.toDispatch;
    return rows.length > 0 && rows.every(b => this.selection.isSelected(b));
  }

  get someSelected(): boolean {
    return this.selection.selected.length > 0 && !this.allSelected;
  }

  toggleAll(): void {
    if (this.allSelected) this.selection.clear();
    else this.toDispatch.forEach(b => this.selection.select(b));
    this.cdr.markForCheck();
  }

  toggleRow(bill: BillResponse): void {
    this.selection.toggle(bill);
    this.cdr.markForCheck();
  }

  isSelected(bill: BillResponse): boolean { return this.selection.isSelected(bill); }

  // ── assigning ───────────────────────────────────────────────────

  assignSelected(): void {
    const bills = this.selection.selected;
    if (bills.length === 0) return;

    this.dialog.open(BulkAssignDialog, {
      data: { bills, workers: this.workers },
      width: '520px',
      maxWidth: '95vw',
    }).afterClosed().subscribe(done => {
      if (done) this.load();   // assigned bills drop off the working list
    });
  }

  assignOne(bill: BillResponse): void {
    this.dialog.open(BulkAssignDialog, {
      data: { bills: [bill], workers: this.workers },
      width: '520px',
      maxWidth: '95vw',
    }).afterClosed().subscribe(done => {
      if (done) this.load();
    });
  }

  /** Days since the bill was raised — how long it has been waiting for collection. */
  ageDays(bill: BillResponse): number {
    const from = new Date(bill.billDate + 'T00:00:00').getTime();
    const today = new Date(); today.setHours(0, 0, 0, 0);
    return Math.round((today.getTime() - from) / 86400000);
  }

  /**
   * Cash and credit age on different scales, so each row is banded by its own type —
   * cash turns urgent within a fortnight, credit runs in 30-day bands. Mixing both in
   * one list stays readable because the colour means the same thing either way:
   * green fine, amber chase, red overdue.
   */
  ageBand(bill: BillResponse): 'ok' | 'warn' | 'late' {
    const days = this.ageDays(bill);
    if (this.isCash(bill)) {
      if (days > 14) return 'late';
      if (days > 7) return 'warn';
      return 'ok';
    }
    if (days > 60) return 'late';
    if (days > 30) return 'warn';
    return 'ok';
  }

  ageTooltip(bill: BillResponse): string {
    const days = this.ageDays(bill);
    if (this.isCash(bill)) {
      const band = days === 0 ? 'raised today'
        : days <= 7 ? 'cash follow-up (1–7 days)'
        : days <= 14 ? 'cash urgent (8–14 days)'
        : 'cash serious (15+ days)';
      return `${days} days old — ${band}`;
    }
    const band = days <= 30 ? 'current (0–30 days)'
      : days <= 60 ? '31–60 days'
      : days <= 90 ? '61–90 days'
      : '91+ days';
    return `${days} days old — credit ${band}${days >= 45 ? ' · overdue' : ''}`;
  }
}
