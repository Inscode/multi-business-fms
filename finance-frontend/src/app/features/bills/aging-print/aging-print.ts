import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AgingExport, AgingExportCustomer, Bill } from '../../../core/services/bill';

/**
 * Print-only aging report. Lives outside the main layout so Ctrl+P (or Save as PDF)
 * captures the report alone — no sidebar, no header.
 *
 * Cash and credit are printed as separate sections: they age on different scales,
 * so a single set of columns would misrepresent one of them.
 */
@Component({
  selector: 'app-aging-print',
  standalone: true,
  imports: [CommonModule, DatePipe, DecimalPipe, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './aging-print.html',
  styleUrl: './aging-print.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AgingPrint implements OnInit {
  data: AgingExport | null = null;
  sort: 'AGE' | 'AMOUNT' = 'AGE';
  loading = true;
  error = false;

  constructor(
    private billService: Bill,
    private route: ActivatedRoute,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    const q = this.route.snapshot.queryParamMap;
    const business = q.get('business') ?? 'RAINCO';
    const area = q.get('area') ?? undefined;
    const billType = q.get('billType') ?? undefined;
    this.sort = (q.get('sort') as 'AGE' | 'AMOUNT') ?? 'AGE';

    this.billService.getAgingExport(business, area, billType, this.sort).subscribe({
      next: (d) => {
        this.data = d;
        this.loading = false;
        this.cdr.detectChanges();
        // Let the tables lay out before the print dialog measures the page
        setTimeout(() => window.print(), 400);
      },
      error: () => { this.error = true; this.loading = false; this.cdr.detectChanges(); },
    });
  }

  print(): void { window.print(); }

  get scopeLine(): string {
    if (!this.data) return '';
    const area = this.data.area ?? 'All areas';
    const type = this.data.billType
      ? (this.data.billType === 'CASH' ? 'Cash bills' : 'Credit bills')
      : 'Cash + Credit';
    const order = this.sort === 'AMOUNT' ? 'Largest amount first' : 'Longest waiting first';
    return `${area}  ·  ${type}  ·  ${order}`;
  }

  // Column totals — computed here so the footer row can't drift from the rows above it
  totalOf(rows: AgingExportCustomer[], field: keyof AgingExportCustomer): number {
    return rows.reduce((sum, r) => sum + (Number(r[field]) || 0), 0);
  }

  billCountOf(rows: AgingExportCustomer[]): number {
    return rows.reduce((sum, r) => sum + r.billCount, 0);
  }
}
