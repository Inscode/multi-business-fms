import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Dashboard, CollectionHealthData, UpcomingChequeEntry, ChequeDetailEntry } from '../../../core/services/dashboard';

@Component({
  selector: 'app-collection-health',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    DecimalPipe,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatTabsModule,
    MatTooltipModule,
  ],
  templateUrl: './collection-health.html',
  styleUrl: './collection-health.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CollectionHealth implements OnInit {
  data: CollectionHealthData | null = null;
  loading = false;
  error = false;
  selectedBusiness = 'RAINCO';

  businesses = ['RAINCO', 'STATIONERY', 'PLASTIC', 'HARDWARE'];

  // ── Cheque date-range picker (Cash Flow tab) ────────────────────
  displayedCheques: UpcomingChequeEntry[] = [];
  chequeFrom: string = this.dateStr(new Date());
  chequeTo: string = this.dateStr(this.addDays(new Date(), 30));
  loadingCheques = false;
  chequeRangeError = '';

  get isDefaultChequeRange(): boolean {
    return this.chequeFrom === this.dateStr(new Date()) && this.chequeTo === this.dateStr(this.addDays(new Date(), 30));
  }

  // Date objects for the Material datepicker bindings
  get chequeFromDate(): Date | null { return this.chequeFrom ? new Date(this.chequeFrom + 'T00:00:00') : null; }
  get chequeToDate():   Date | null { return this.chequeTo   ? new Date(this.chequeTo   + 'T00:00:00') : null; }

  onChequeFromDateChange(e: { value: Date | null }): void {
    this.chequeFrom = e.value ? this.dateStr(e.value) : '';
  }

  onChequeToDateChange(e: { value: Date | null }): void {
    this.chequeTo = e.value ? this.dateStr(e.value) : '';
  }

  constructor(
    private dashboardService: Dashboard,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.error = false;
    this.cdr.detectChanges();
    this.dashboardService.getCollectionHealth(this.selectedBusiness).subscribe({
      next: (r) => {
        this.data = r;
        this.loading = false;
        if (this.isDefaultChequeRange) {
          this.displayedCheques = r.upcomingCheques ?? [];
        } else {
          this.loadChequeRange(); // keep the user's custom range when switching business
        }
        this.cdr.detectChanges();
      },
      error: () => { this.error = true; this.loading = false; this.cdr.detectChanges(); },
    });
  }

  private dateStr(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  private addDays(d: Date, n: number): Date {
    const copy = new Date(d);
    copy.setDate(copy.getDate() + n);
    return copy;
  }

  loadChequeRange(): void {
    if (!this.chequeFrom || !this.chequeTo) return;
    if (this.chequeFrom > this.chequeTo) {
      this.chequeRangeError = '"From" date must be before "To" date.';
      this.cdr.detectChanges();
      return;
    }
    this.chequeRangeError = '';
    this.loadingCheques = true;
    this.cdr.detectChanges();
    this.dashboardService.getUpcomingCheques(this.selectedBusiness, this.chequeFrom, this.chequeTo).subscribe({
      next: (cheques) => { this.displayedCheques = cheques; this.loadingCheques = false; this.cdr.detectChanges(); },
      error: () => { this.loadingCheques = false; this.cdr.detectChanges(); },
    });
  }

  resetChequeRange(): void {
    this.chequeFrom = this.dateStr(new Date());
    this.chequeTo = this.dateStr(this.addDays(new Date(), 30));
    this.chequeRangeError = '';
    this.displayedCheques = this.data?.upcomingCheques ?? [];
    this.cdr.detectChanges();
  }

  // ── DSO trend bar chart helpers ───────────────────────────────
  get maxDsoDays(): number {
    if (!this.data?.dsoTrend?.length) return 1;
    return Math.max(1, ...this.data.dsoTrend.map(e => e.avgCollectionDays));
  }

  barHeightPct(days: number): number {
    return Math.max(4, Math.round((days / this.maxDsoDays) * 100));
  }

  monthLabel(month: string): string {
    const [y, m] = month.split('-');
    const date = new Date(Number(y), Number(m) - 1, 1);
    return date.toLocaleDateString('en-GB', { month: 'short', year: '2-digit' });
  }

  trendClass(days: number): string {
    if (days <= 30) return 'age-ok';
    if (days <= 45) return 'age-caution';
    if (days <= 60) return 'age-warn';
    return 'age-critical';
  }

  // ── Collector leaderboard helpers ──────────────────────────────
  partialRateClass(rate: number): string {
    if (rate <= 0.1) return 'age-ok';
    if (rate <= 0.25) return 'age-caution';
    if (rate <= 0.5) return 'age-warn';
    return 'age-critical';
  }

  // ── Risky customers helpers ────────────────────────────────────
  riskClass(score: number): string {
    if (score <= 1) return 'age-caution';
    if (score <= 3) return 'age-warn';
    return 'age-critical';
  }

  // ── Cheque detail drill-down ────────────────────────────────────
  selectedChequeDate: string | null = null;
  chequeDetails: ChequeDetailEntry[] = [];
  loadingChequeDetails = false;

  onChequeRowClick(date: string): void {
    if (this.selectedChequeDate === date) {
      this.selectedChequeDate = null;
      this.chequeDetails = [];
      this.cdr.detectChanges();
      return;
    }
    this.selectedChequeDate = date;
    this.loadingChequeDetails = true;
    this.chequeDetails = [];
    this.cdr.detectChanges();
    this.dashboardService.getChequeDetails(this.selectedBusiness, date).subscribe({
      next: (details) => { this.chequeDetails = details; this.loadingChequeDetails = false; this.cdr.detectChanges(); },
      error: () => { this.loadingChequeDetails = false; this.cdr.detectChanges(); },
    });
  }

  chequeStatusLabel(status: string): string {
    switch (status) {
      case 'ENTERED': return 'Pending';
      case 'CONFIRMED': return 'Confirmed';
      default: return status;
    }
  }

  // ── Cash flow: tomorrow's deposit callout ───────────────────────
  get tomorrowDateStr(): string {
    const d = new Date();
    d.setDate(d.getDate() + 1);
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  get tomorrowCheques(): { totalAmount: number; count: number } | null {
    const entry = (this.data?.upcomingCheques ?? [])
      .find(c => c.chequeDate === this.tomorrowDateStr);
    return entry ? { totalAmount: entry.totalAmount, count: entry.count } : null;
  }

  // ── Payment mix helpers ─────────────────────────────────────────
  get totalCash(): number {
    return (this.data?.paymentMix ?? []).reduce((s, e) => s + e.cash, 0);
  }
  get totalCheque(): number {
    return (this.data?.paymentMix ?? []).reduce((s, e) => s + e.cheque, 0);
  }
  get totalBankTransfer(): number {
    return (this.data?.paymentMix ?? []).reduce((s, e) => s + e.bankTransfer, 0);
  }

  mixSegPct(amount: number, total: number): number {
    return total > 0 ? (amount / total) * 100 : 0;
  }

  trackByMonth(_i: number, e: { month: string }): string { return e.month; }
  trackByWorker(_i: number, e: { workerId: number }): number { return e.workerId; }
  trackByDate(_i: number, e: { chequeDate: string }): string { return e.chequeDate; }
  trackByCustomer(_i: number, e: { customerName: string }): string { return e.customerName; }
  trackByMixDate(_i: number, e: { date: string }): string { return e.date; }
}
