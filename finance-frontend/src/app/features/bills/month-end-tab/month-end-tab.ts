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
  rows: BillAuditRow[] = [];

  loading = false;
  error = '';
  markingBillId: number | null = null;

  // Scope — empty means everything
  month = new Date().toISOString().slice(0, 7);   // "2026-08"
  selectedBusiness = '';
  selectedArea = '';

  businesses = ['RAINCO', 'RETAIL_SHOP', 'STATIONERY', 'PLASTIC', 'HARDWARE'];
  areas: string[] = [];

  view: ViewMode = 'TO_CHECK';
  search = '';

  constructor(
    private audit: BillAuditService,
    private dialog: MatDialog,
    private router: Router,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void { this.startOrResume(); }

  // ── session ─────────────────────────────────────────────────────

  startOrResume(): void {
    this.loading = true;
    this.error = '';
    this.cdr.markForCheck();

    const monthStart = `${this.month}-01`;
    this.audit.openSession(monthStart, this.selectedBusiness || undefined, this.selectedArea || undefined)
      .subscribe({
        next: (s) => { this.session = s; this.loadRows(); },
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

  get toCheck(): BillAuditRow[]       { return this.rows.filter(r => !r.markType); }
  get inHand(): BillAuditRow[]        { return this.rows.filter(r => r.markType === 'IN_HAND'); }
  get paidNotEntered(): BillAuditRow[] { return this.rows.filter(r => r.markType === 'PAID_NOT_ENTERED'); }
  get missing(): BillAuditRow[]       { return this.rows.filter(r => r.markType === 'MISSING'); }

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

  onScopeChange(): void { this.startOrResume(); }

  // ── marking ─────────────────────────────────────────────────────

  mark(row: BillAuditRow, type: BillAuditMarkType | null): void {
    if (!this.session) return;
    this.markingBillId = row.billId;
    this.cdr.markForCheck();

    this.audit.mark(this.session.id, row.billId, type).subscribe({
      next: (updated) => {
        const i = this.rows.findIndex(r => r.billId === updated.billId);
        if (i >= 0) this.rows[i] = updated;
        this.rows = [...this.rows];
        this.markingBillId = null;
        this.cdr.markForCheck();
      },
      error: () => { this.markingBillId = null; this.cdr.markForCheck(); },
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
        next: (s) => { this.session = s; this.cdr.markForCheck(); },
      });
    });
  }

  get monthLabel(): string {
    const [y, m] = this.month.split('-').map(Number);
    return new Date(y, m - 1, 1).toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
  }

  get progressPct(): number {
    if (this.rows.length === 0) return 0;
    return Math.round(((this.rows.length - this.toCheck.length) / this.rows.length) * 100);
  }
}
