import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SelectionModel } from '@angular/cdk/collections';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
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
    MatButtonModule, MatIconModule, MatSelectModule, MatFormFieldModule,
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
  selectedArea = '';
  showAssigned = false;

  businesses = ['', 'RAINCO', 'RETAIL_SHOP', 'STATIONERY', 'PLASTIC', 'HARDWARE'];

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
        this.allBills = bills.filter(b => b.balanceRemaining > 0 && b.status !== 'CANCELLED');
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
      next: (w) => { this.workers = w.filter(x => x.active); this.cdr.markForCheck(); },
      error: () => { this.workers = []; this.cdr.markForCheck(); },
    });
  }

  // ── filtering ───────────────────────────────────────────────────

  private get inScope(): BillResponse[] {
    return this.allBills.filter(b =>
      (!this.selectedBusiness || b.business === this.selectedBusiness) &&
      (!this.selectedArea || b.area === this.selectedArea));
  }

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
    this.selectedArea = '';
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
}
