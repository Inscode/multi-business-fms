import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { BillAuditMarkType, BillAuditRow, BillAuditService, BillAuditSession } from '../../../core/services/bill-audit';
import { ConfirmDialog } from '../../../shared/confirm-dialog/confirm-dialog';

type ViewMode = 'TO_CHECK' | 'PAID_NOT_ENTERED' | 'MISSING' | 'IN_HAND';

/**
 * Month-end reconciliation. The system lists every bill it still shows as owing;
 * you tick off the ones physically in the file and they leave the working list.
 * What's left is the exception list — paid but never entered, or genuinely missing.
 */
@Component({
  selector: 'app-month-end-tab',
  standalone: true,
  imports: [
    CommonModule, FormsModule, DatePipe, DecimalPipe,
    MatButtonModule, MatIconModule, MatSelectModule, MatFormFieldModule,
    MatInputModule, MatProgressSpinnerModule, MatDialogModule, MatTooltipModule,
  ],
  templateUrl: './month-end-tab.html',
  styleUrl: './month-end-tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MonthEndTab implements OnInit {
  session: BillAuditSession | null = null;
  sessions: BillAuditSession[] = [];
  rows: BillAuditRow[] = [];

  loading = false;
  error = '';
  markingBillId: number | null = null;

  // Scope — empty means everything
  month = new Date().toISOString().slice(0, 7);   // "2026-08"
  selectedBusiness = '';
  selectedAreas: string[] = [];

  businesses = ['RAINCO', 'RETAIL_SHOP', 'STATIONERY', 'PLASTIC', 'HARDWARE', 'MIX'];
  areas: string[] = [];

  view: ViewMode = 'TO_CHECK';
  search = '';

  constructor(
    private audit: BillAuditService,
    private dialog: MatDialog,
    private router: Router,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void { this.loadSessions(true); }

  // ── sessions ────────────────────────────────────────────────────

  /** Everyone sees every check; only your own (or any, if admin) can be marked. */
  loadSessions(selectMine = false): void {
    this.audit.listSessions().subscribe({
      next: (list) => {
        this.sessions = list;
        if (selectMine) {
          const monthStart = `${this.month}-01`;
          const mine = list.find(s => s.periodMonth === monthStart && s.mine && !s.closedAt);
          const target = mine ?? list[0] ?? null;
          if (target) this.selectSession(target.id);
          else { this.loading = false; this.cdr.markForCheck(); }
        } else {
          this.cdr.markForCheck();
        }
      },
      error: () => { this.loading = false; this.cdr.markForCheck(); },
    });
  }

  selectSession(sessionId: number): void {
    const found = this.sessions.find(s => s.id === sessionId);
    if (!found) return;
    this.session = found;
    this.month = found.periodMonth.slice(0, 7);
    this.view = 'TO_CHECK';
    this.loading = true;
    this.error = '';
    this.cdr.markForCheck();
    this.loadRows();
  }

  /** True when the selected check is someone else's — marks are locked. */
  get readOnly(): boolean { return !!this.session && !this.session.canEdit; }

  get ownerLabel(): string {
    return this.session?.openedByName ?? 'someone else';
  }

  sessionLabel(s: BillAuditSession): string {
    const [y, m] = s.periodMonth.split('-').map(Number);
    const month = new Date(y, m - 1, 1).toLocaleDateString('en-US', { month: 'short', year: 'numeric' });
    const owner = s.mine ? 'You' : (s.openedByName ?? 'Unknown');
    const state = s.closedAt ? ' · closed' : '';
    return `${month} — ${owner} · ${s.unchecked} left${state}`;
  }

  startOrResume(): void {
    this.loading = true;
    this.error = '';
    this.cdr.markForCheck();

    const monthStart = `${this.month}-01`;
    this.audit.openSession(monthStart).subscribe({
      next: (s) => {
        this.session = s;
        this.loadSessions();   // refresh the picker so the new sweep appears
        this.loadRows();
      },
      error: () => { this.error = 'Could not start the check.'; this.loading = false; this.cdr.markForCheck(); },
    });
  }

  private loadRows(): void {
    if (!this.session) return;
    this.audit.getRows(this.session.id).subscribe({
      next: (rows) => {
        this.rows = rows;
        this.areas = [...new Set(rows.map(r => r.area).filter((a): a is string => !!a))].sort();
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: () => { this.error = 'Could not load bills.'; this.loading = false; this.cdr.markForCheck(); },
    });
  }

  // ── counts + filtering ──────────────────────────────────────────

  private bySearch(rows: BillAuditRow[]): BillAuditRow[] {
    const q = this.search.toLowerCase().trim();
    if (!q) return rows;
    return rows.filter(r =>
      r.billNumber.toLowerCase().includes(q) ||
      r.customerName.toLowerCase().includes(q));
  }

  /**
   * Business/area narrow what's on screen only — the sweep itself always covers the
   * whole month, so a bill marked with no filter stays marked once you filter.
   */
  private get scoped(): BillAuditRow[] {
    return this.rows.filter(r =>
      (!this.selectedBusiness || r.business === this.selectedBusiness) &&
      (this.selectedAreas.length === 0 || (!!r.area && this.selectedAreas.includes(r.area))));
  }

  get toCheck(): BillAuditRow[]        { return this.scoped.filter(r => !r.markType); }
  get inHand(): BillAuditRow[]         { return this.scoped.filter(r => r.markType === 'IN_HAND'); }
  get paidNotEntered(): BillAuditRow[] { return this.scoped.filter(r => r.markType === 'PAID_NOT_ENTERED'); }
  get missing(): BillAuditRow[]        { return this.scoped.filter(r => r.markType === 'MISSING'); }

  get visibleRows(): BillAuditRow[] {
    const source =
      this.view === 'TO_CHECK'         ? this.toCheck :
      this.view === 'IN_HAND'          ? this.inHand :
      this.view === 'PAID_NOT_ENTERED' ? this.paidNotEntered :
                                         this.missing;
    return this.bySearch(source);
  }

  get exceptionCount(): number {
    return this.paidNotEntered.length + this.missing.length;
  }

  get visibleTotal(): number {
    return this.visibleRows.reduce((sum, r) => sum + r.balanceRemaining, 0);
  }

  setView(view: ViewMode): void { this.view = view; this.cdr.markForCheck(); }

  /**
   * Changing the month switches to your own sweep for it when you have one; otherwise
   * it just re-points the view and the "Start my check" button appears.
   */
  onMonthChange(): void {
    const monthStart = `${this.month}-01`;
    const mine = this.sessions.find(s => s.periodMonth === monthStart && s.mine && !s.closedAt);
    if (mine) { this.selectSession(mine.id); return; }

    const other = this.sessions.find(s => s.periodMonth === monthStart);
    if (other) { this.selectSession(other.id); return; }

    this.session = null;
    this.rows = [];
    this.loading = false;
    this.cdr.markForCheck();
  }

  /** Have you already got your own check open for the picked month? */
  get hasOwnSessionForMonth(): boolean {
    const monthStart = `${this.month}-01`;
    return this.sessions.some(s => s.periodMonth === monthStart && s.mine && !s.closedAt);
  }

  /** Business/area only narrow the view — marks are untouched. */
  onFilterChange(): void { this.cdr.markForCheck(); }

  clearFilters(): void {
    this.selectedBusiness = '';
    this.selectedAreas = [];
    this.cdr.markForCheck();
  }

  // ── marking ─────────────────────────────────────────────────────

  mark(row: BillAuditRow, type: BillAuditMarkType | null): void {
    if (!this.session || this.readOnly) return;
    this.markingBillId = row.billId;
    this.cdr.markForCheck();

    this.audit.mark(this.session.id, row.billId, type).subscribe({
      next: (updated) => {
        const i = this.rows.findIndex(r => r.billId === updated.billId);
        if (i >= 0) this.rows[i] = updated;
        this.rows = [...this.rows];
        this.markingBillId = null;
        this.loadSessions();   // keep the picker's "left" counts honest
        this.cdr.markForCheck();
      },
      error: (e) => {
        this.error = e?.error?.message ?? 'Could not save that mark.';
        this.markingBillId = null;
        this.cdr.markForCheck();
      },
    });
  }

  /** Straight to payment entry with the bill preselected. */
  enterPayment(row: BillAuditRow): void {
    this.router.navigate(['/payments/enter'], {
      state: {
        preselectedBill: {
          id: row.billId,
          billNumber: row.billNumber,
          customerName: row.customerName,
          balanceRemaining: row.balanceRemaining,
        },
      },
    });
  }

  openBill(row: BillAuditRow): void {
    this.router.navigate(['/bills', row.billId]);
  }

  closeSession(): void {
    if (!this.session) return;
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Close this check',
        message: `Close the ${this.monthLabel} check? ${this.toCheck.length} bill(s) are still unchecked — they stay recorded as unchecked.`,
        confirmText: 'Close',
        confirmColor: 'primary',
      },
      maxWidth: '95vw',
    }).afterClosed().subscribe(result => {
      if (!result?.confirmed) return;
      this.audit.closeSession(this.session!.id).subscribe({
        next: (s) => { this.session = s; this.loadSessions(); this.cdr.markForCheck(); },
      });
    });
  }

  get monthLabel(): string {
    const [y, m] = this.month.split('-').map(Number);
    return new Date(y, m - 1, 1).toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
  }

  get progressPct(): number {
    const total = this.scoped.length;
    if (total === 0) return 0;
    return Math.round(((total - this.toCheck.length) / total) * 100);
  }

  /** True when a business/area filter is narrowing the month's full list. */
  get isFiltered(): boolean {
    return !!this.selectedBusiness || this.selectedAreas.length > 0;
  }

  /** Human-readable area scope for the filter note. */
  get areaLabel(): string {
    if (this.selectedAreas.length === 0) return '';
    if (this.selectedAreas.length <= 2) return this.selectedAreas.join(', ');
    return `${this.selectedAreas.length} areas`;
  }

  get scopedCount(): number { return this.scoped.length; }
}
