import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Bill, AgingReportResponse, AgingAreaSummary, AgingCustomerEntry } from '../../../core/services/bill';
import { BillFilterState } from '../../../core/services/bill-filter-state';
import { Auth } from '../../../core/services/auth';

interface PriorityBucket {
  label: string;
  cssClass: string;
  customers: AgingCustomerEntry[];
  bucketTotal: number;
}

@Component({
  selector: 'app-aging-report',
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
    MatTabsModule,
    MatTooltipModule,
  ],
  templateUrl: './aging-report.html',
  styleUrl: './aging-report.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AgingReport implements OnInit {
  report: AgingReportResponse | null = null;
  loading = false;
  error = false;
  selectedBusiness = 'RAINCO';
  expandedAreas = new Set<string>();

  // ── Export scope (print / Excel) ────────────────────────────────
  /**
    * Areas to print. Empty means every area — a rep's round rarely maps onto one area,
    * and printing a sheet per area to staple together helps nobody.
    */
  exportSelectedAreas: string[] = [];
  exportBillType: '' | 'CASH' | 'CREDIT' = '';
  exportSort: 'AGE' | 'AMOUNT' = 'AGE';
  downloading = false;

  /** Areas present in the loaded report — what can actually be printed. */
  get exportAreas(): string[] {
    return (this.report?.byArea ?? [])
      .map(a => a.area)
      .filter(a => !!a && a !== 'Unknown')
      .sort();
  }

  /** What the scope reads as on screen, before anything is printed. */
  get areaScopeLabel(): string {
    const n = this.exportSelectedAreas.length;
    if (n === 0) return 'All areas';
    if (n === 1) return this.exportSelectedAreas[0];
    if (n <= 3) return this.exportSelectedAreas.join(', ');
    return `${n} areas`;
  }

  selectAllAreas(): void {
    this.exportSelectedAreas = [];
    this.cdr.markForCheck();
  }

  private exportParams(): Record<string, string> {
    const params: Record<string, string> = { business: this.selectedBusiness };
    // Joined rather than repeated, so the same parameter serves one area or many.
    if (this.exportSelectedAreas.length) {
      params['area'] = this.exportSelectedAreas.join(',');
    }
    if (this.exportBillType) params['billType'] = this.exportBillType;
    params['sort'] = this.exportSort;
    return params;
  }

  /** Opens the print-only view in a new tab; it prints itself once rendered. */
  printReport(): void {
    const qs = new URLSearchParams(this.exportParams()).toString();
    window.open(`/bills/aging/print?${qs}`, '_blank');
  }

  /**
   * The short sheet: one line per customer, portrait, credit and cash on separate
   * pages.
   *
   * <p>The wide report answers where the money is sitting; this one answers who to
   * call today, and prints on a dot matrix without shading every second row.
   *
   * <p>Bill type is deliberately not passed on. The sheet splits credit from cash
   * itself, so filtering to one of them here would leave a page blank.
   */
  printCompact(): void {
    const params = this.exportParams();
    delete params['billType'];
    delete params['sort'];
    const qs = new URLSearchParams(params).toString();
    window.open(`/bills/aging/print-compact?${qs}`, '_blank');
  }

  downloadExcel(): void {
    this.downloading = true;
    this.cdr.markForCheck();
    this.billService.downloadAgingExcel(
      this.selectedBusiness,
      this.exportSelectedAreas.length ? this.exportSelectedAreas.join(',') : undefined,
      this.exportBillType || undefined,
      this.exportSort,
    ).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `aging-${this.selectedBusiness.toLowerCase()}`
          + (this.exportSelectedAreas.length === 1
              ? `-${this.exportSelectedAreas[0].toLowerCase().replace(/ /g, '-')}`
              : this.exportSelectedAreas.length > 1
                ? `-${this.exportSelectedAreas.length}-areas`
                : '')
          + (this.exportBillType ? `-${this.exportBillType.toLowerCase()}` : '')
          + `-${new Date().toISOString().slice(0, 10)}.xlsx`;
        a.click();
        URL.revokeObjectURL(url);
        this.downloading = false;
        this.cdr.markForCheck();
      },
      error: () => { this.downloading = false; this.cdr.markForCheck(); },
    });
  }

  get businesses(): string[] {
    return this.auth.isDemo ? ['DEMO'] : ['RAINCO', 'STATIONERY', 'PLASTIC', 'HARDWARE', 'MIX'];
  }

  get isDemo(): boolean { return this.auth.isDemo; }

  constructor(
    private billService: Bill,
    private cdr: ChangeDetectorRef,
    private router: Router,
    private filterState: BillFilterState,
    private auth: Auth,
  ) {}

  ngOnInit(): void {
    if (this.auth.isDemo) this.selectedBusiness = 'DEMO';
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = false;
    this.cdr.detectChanges();
    this.billService.getAgingReport(this.selectedBusiness).subscribe({
      next: (r) => { this.report = r; this.loading = false; this.cdr.detectChanges(); },
      error: () => { this.error = true; this.loading = false; this.cdr.detectChanges(); },
    });
  }

  // ── Collection priority buckets ───────────────────────────────
  get priorityBuckets(): PriorityBucket[] {
    if (!this.report?.allCustomers) return [];
    const all = this.report.allCustomers;

    const bucket91 = all.filter(c => c.days91plus > 0)
      .sort((a, b) => b.days91plus - a.days91plus);
    const bucket61 = all.filter(c => c.days91plus === 0 && c.days61to90 > 0)
      .sort((a, b) => b.days61to90 - a.days61to90);
    const bucket31 = all.filter(c => c.days91plus === 0 && c.days61to90 === 0 && c.days31to60 > 0)
      .sort((a, b) => b.days31to60 - a.days31to60);
    const bucket0  = all.filter(c => c.days91plus === 0 && c.days61to90 === 0 && c.days31to60 === 0 && c.current > 0)
      .sort((a, b) => b.current - a.current);

    return [
      { label: '91+ Days', cssClass: 'bucket-critical', customers: bucket91, bucketTotal: bucket91.reduce((s, c) => s + c.days91plus, 0) },
      { label: '61–90 Days', cssClass: 'bucket-warn', customers: bucket61, bucketTotal: bucket61.reduce((s, c) => s + c.days61to90, 0) },
      { label: '31–60 Days', cssClass: 'bucket-caution', customers: bucket31, bucketTotal: bucket31.reduce((s, c) => s + c.days31to60, 0) },
      { label: '0–30 Days', cssClass: 'bucket-ok', customers: bucket0, bucketTotal: bucket0.reduce((s, c) => s + c.current, 0) },
    ].filter(b => b.customers.length > 0);
  }

  // ── Cash priority buckets ─────────────────────────────────────
  get cashPriorityBuckets(): PriorityBucket[] {
    if (!this.report?.allCustomers) return [];
    const all = this.report.allCustomers.filter(c => c.cashPending > 0);

    const serious  = all.filter(c => c.cashSerious > 0)
      .sort((a, b) => b.cashSerious - a.cashSerious);
    const urgent   = all.filter(c => c.cashSerious === 0 && c.cashUrgent > 0)
      .sort((a, b) => b.cashUrgent - a.cashUrgent);
    const followUp = all.filter(c => c.cashSerious === 0 && c.cashUrgent === 0 && c.cashFollowUp > 0)
      .sort((a, b) => b.cashFollowUp - a.cashFollowUp);

    return [
      { label: 'Serious (15+ days)', cssClass: 'bucket-critical', customers: serious,  bucketTotal: serious.reduce((s, c)  => s + c.cashSerious,  0) },
      { label: 'Urgent (8–14 days)', cssClass: 'bucket-warn',     customers: urgent,   bucketTotal: urgent.reduce((s, c)   => s + c.cashUrgent,   0) },
      { label: 'Follow Up (1–7d)',   cssClass: 'bucket-caution',  customers: followUp, bucketTotal: followUp.reduce((s, c) => s + c.cashFollowUp, 0) },
    ].filter(b => b.customers.length > 0);
  }

  // ── Area helpers ──────────────────────────────────────────────
  toggleArea(area: string): void {
    if (this.expandedAreas.has(area)) this.expandedAreas.delete(area);
    else this.expandedAreas.add(area);
    this.cdr.detectChanges();
  }

  isExpanded(area: string): boolean { return this.expandedAreas.has(area); }

  expandAll(): void {
    this.report?.byArea.forEach(a => this.expandedAreas.add(a.area));
    this.cdr.detectChanges();
  }

  collapseAll(): void { this.expandedAreas.clear(); this.cdr.detectChanges(); }

  goCollectArea(area: AgingAreaSummary): void {
    this.filterState.save({
      filterFrom: '',
      filterTo: '',
      selectedBusiness: this.selectedBusiness,
      selectedStatus: '',
      selectedArea: area.area,
      hideCompleted: true,
      overdueMode: false,
      searchQuery: '',
      pageIndex: 0,
    });
    this.router.navigate(['/bills']);
  }

  // ── Shared helpers ─────────────────────────────────────────────
  hasCreditBills(c: AgingCustomerEntry): boolean {
    return (c.current + c.days31to60 + c.days61to90 + c.days91plus) > 0;
  }

  hasCashBills(c: AgingCustomerEntry): boolean {
    return c.cashPending > 0;
  }

  agingBucket(entry: AgingCustomerEntry): string {
    if (entry.days91plus > 0) return '91+';
    if (entry.days61to90 > 0) return '61–90';
    if (entry.days31to60 > 0) return '31–60';
    return '0–30';
  }

  agingClass(entry: AgingCustomerEntry): string {
    const b = this.agingBucket(entry);
    if (b === '91+') return 'age-critical';
    if (b === '61–90') return 'age-warn';
    if (b === '31–60') return 'age-caution';
    return 'age-ok';
  }

  cashAgingBucket(c: AgingCustomerEntry): string {
    if (c.cashSerious > 0) return 'Serious';
    if (c.cashUrgent > 0) return 'Urgent';
    if (c.cashFollowUp > 0) return 'Follow Up';
    return 'Normal';
  }

  cashAgingClass(c: AgingCustomerEntry): string {
    const b = this.cashAgingBucket(c);
    if (b === 'Serious') return 'age-critical';
    if (b === 'Urgent') return 'age-warn';
    if (b === 'Follow Up') return 'age-caution';
    return 'age-ok';
  }

  lastPaymentLabel(date: string | null): string {
    if (!date) return 'Never';
    const d = new Date(date);
    return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' });
  }

  lastPaymentClass(date: string | null): string {
    if (!date) return 'pmt-never';
    const days = Math.floor((Date.now() - new Date(date).getTime()) / 86400000);
    if (days > 60) return 'pmt-old';
    if (days > 30) return 'pmt-warn';
    return 'pmt-recent';
  }

  trackByName(_i: number, e: AgingCustomerEntry): string { return e.customerName + e.area; }
  trackByArea(_i: number, a: AgingAreaSummary): string { return a.area; }
  trackByLabel(_i: number, b: PriorityBucket): string { return b.label; }
}
