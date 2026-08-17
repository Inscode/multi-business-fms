import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatNativeDateModule } from '@angular/material/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router } from '@angular/router';
import {
  CollectionBand,
  LateCollectionCustomer,
  LateCollectionReport,
  LateCollectionService,
} from '../../../core/services/late-collection';
import { localDateStr } from '../../../core/utils/date-utils';
import { CustomerHealthDialog }
  from '../../../shared/customer-health-dialog/customer-health-dialog';

/**
 * How long the money collected in a period took to come in.
 *
 * <p>Built around one number: what was collected after the 70 days the company itself
 * gets to pay the principal. Past that line the sale was funded out of the company's own
 * cash, and nothing else in the system records it — the bill closes, the balance goes to
 * zero, and that it closed twenty days too late leaves no trace anywhere.
 */
@Component({
  selector: 'app-late-collections',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatDividerModule,
    MatProgressSpinnerModule,
    MatTabsModule,
    MatTooltipModule,
    MatDialogModule,
  ],
  templateUrl: './late-collections.html',
  styleUrl: './late-collections.scss',
})
export class LateCollections implements OnInit {
  report: LateCollectionReport | null = null;
  loading = false;
  error = '';

  business: string | null = 'RAINCO';
  readonly businesses = [
    { value: null, label: 'All businesses' },
    { value: 'RAINCO', label: 'Rainco' },
    { value: 'STATIONERY', label: 'Stationery' },
    { value: 'PLASTIC', label: 'Plastic' },
  ];

  from: Date;
  to: Date;

  /** Which band the payment list is narrowed to, or null for everything. */
  bandFilter: CollectionBand | null = null;

  constructor(
    private service: LateCollectionService,
    private cdr: ChangeDetectorRef,
    private dialog: MatDialog,
    private router: Router,
  ) {
    // Defaults to the month so far. The report is normally read at month end against
    // what the bank shows, and that is the period being reconciled.
    const now = new Date();
    this.from = new Date(now.getFullYear(), now.getMonth(), 1);
    this.to = now;
  }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.error = '';
    // localDateStr, never toISOString: the latter shifts by the timezone offset and
    // would quietly move a period boundary by a day.
    this.service.get(this.business, localDateStr(this.from), localDateStr(this.to)).subscribe({
      next: (r) => {
        this.report = r;
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Could not load collections.';
        this.loading = false;
        this.cdr.markForCheck();
      },
    });
  }

  /** Presets, because the period wanted is nearly always a whole month. */
  setMonth(offset: number): void {
    const now = new Date();
    const first = new Date(now.getFullYear(), now.getMonth() + offset, 1);
    const last = new Date(now.getFullYear(), now.getMonth() + offset + 1, 0);
    this.from = first;
    this.to = offset === 0 ? now : last;
    this.load();
  }

  setBand(band: CollectionBand | null): void {
    this.bandFilter = this.bandFilter === band ? null : band;
    this.cdr.markForCheck();
  }

  get visiblePayments() {
    if (!this.report) return [];
    const rows = this.bandFilter
      ? this.report.payments.filter(p => p.band === this.bandFilter)
      : this.report.payments;
    // Capped: the point is the late end of the list, which is already at the top since
    // the server sorts oldest money first.
    return rows.slice(0, 200);
  }

  get hiddenPaymentCount(): number {
    if (!this.report) return 0;
    const total = this.bandFilter
      ? this.report.payments.filter(p => p.band === this.bandFilter).length
      : this.report.payments.length;
    return Math.max(0, total - 200);
  }

  /** Only the customers who were actually late — the rest is noise on this page. */
  get lateCustomers(): LateCollectionCustomer[] {
    return (this.report?.customers ?? []).filter(c => c.pastDangerAmount > 0);
  }

  bandClass(band: CollectionBand): string {
    switch (band) {
      case 'ON_TIME':      return 'b-ontime';
      case 'WATCH':        return 'b-watch';
      case 'LATE':         return 'b-late';
      case 'BEYOND_TERMS': return 'b-beyond';
      default:             return '';
    }
  }

  openCustomerHealth(c: LateCollectionCustomer): void {
    if (!c.customerId) return;
    this.dialog.open(CustomerHealthDialog, {
      data: { customerId: c.customerId, customerName: c.customerName },
      width: '760px',
      maxWidth: '95vw',
    });
  }

  openBill(id: number): void {
    this.router.navigate(['/bills', id]);
  }

  /**
   * The period's collections as a CSV.
   *
   * <p>Built as a table rather than offered as a download link — the artifact sandbox
   * aside, a finance figure gets checked in a spreadsheet, and this is the shape that
   * opens cleanly in one.
   */
  exportCsv(): void {
    if (!this.report) return;
    const head = ['Bill', 'Customer', 'Area', 'Bill Date', 'Paid On', 'Days', 'Band', 'Amount'];
    const lines = [head.join(',')];
    for (const p of this.report.payments) {
      lines.push([
        p.billNumber,
        `"${(p.customerName ?? '').replace(/"/g, '""')}"`,
        `"${p.area ?? ''}"`,
        p.billDate,
        p.paymentDate,
        String(p.days),
        p.band,
        String(p.amount),
      ].join(','));
    }
    const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `late-collections-${this.report.from}-to-${this.report.to}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }
}
