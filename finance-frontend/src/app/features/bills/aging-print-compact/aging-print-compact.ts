import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AgingExport, AgingExportCustomer, Bill } from '../../../core/services/bill';

/**
 * The aging report at one line per customer, for a dot matrix printer.
 *
 * <p>The wide report answers "where is the money sitting"; this one answers "who do I
 * call today". Six columns, portrait, no shading — a page a collector can hold, and a
 * page a nine-pin printer renders in one pass instead of shading every second row.
 *
 * <p>Credit and cash are printed as separate sheets. They age on different clocks —
 * credit runs to seventy days, cash is due on delivery — so one "days overdue" column
 * cannot describe both without lying about one of them.
 */
@Component({
  selector: 'app-aging-print-compact',
  standalone: true,
  imports: [CommonModule, DatePipe, DecimalPipe, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './aging-print-compact.html',
  styleUrl: './aging-print-compact.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AgingPrintCompact implements OnInit {

  readonly companyName = 'Ghanim Enterprises';

  /**
   * Where each kind of bill stops being merely open and starts being late.
   *
   * <p>Kept beside each other so the two sheets can say plainly what their own column
   * is counting against, rather than leaving the reader to assume the credit run.
   */
  readonly creditTermsDays = 45;
  readonly cashTermsDays = 7;

  data: AgingExport | null = null;
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

    // Both kinds are always fetched: the sheets are split here, not by the filter, so
    // one run of the report produces the whole set rather than two trips.
    this.billService.getAgingExport(business, area, undefined, 'AGE').subscribe({
      next: (d) => {
        this.data = d;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => { this.error = true; this.loading = false; this.cdr.detectChanges(); },
    });
  }

  print(): void { window.print(); }
  close(): void { window.close(); }

  get scopeLine(): string {
    return this.data?.area ?? 'All areas';
  }

  /**
   * How long the customer's oldest open bill has been waiting.
   *
   * <p>Counted from the bill date, which is the only date the report carries. A bill
   * dated today reads zero rather than negative, so a clock wrong by a day cannot
   * produce a figure that looks like a fault.
   */
  ageDays(c: AgingExportCustomer): number {
    if (!c.oldestBillDate) return 0;
    const from = new Date(c.oldestBillDate + 'T00:00:00').getTime();
    const days = Math.floor((Date.now() - from) / 86400000);
    return days > 0 ? days : 0;
  }

  /** Days past the terms for that kind of bill, or zero while still inside them. */
  overdueDays(c: AgingExportCustomer, cash: boolean): number {
    const over = this.ageDays(c) - (cash ? this.cashTermsDays : this.creditTermsDays);
    return over > 0 ? over : 0;
  }

  /**
   * The mark beside a late customer.
   *
   * <p>Characters rather than colour or shading: a dot matrix has no colour, prints a
   * grey fill as a heavy dot pattern that blurs the text beneath it, and is often read
   * from a copy taken after the ribbon has faded. Asterisks survive all three.
   */
  flag(c: AgingExportCustomer, cash: boolean): string {
    const over = this.overdueDays(c, cash);
    if (over <= 0) return '';
    if (cash)  return over > 23 ? '***' : over > 8 ? '**' : '*';
    return over > 25 ? '***' : over > 15 ? '**' : '*';
  }

  /** Customers with credit still owing, worst first. */
  get creditRows(): AgingExportCustomer[] {
    return (this.data?.creditCustomers ?? [])
      .filter(c => c.totalOutstanding - c.cashPending > 0)
      .sort((a, b) => this.ageDays(b) - this.ageDays(a));
  }

  get cashRows(): AgingExportCustomer[] {
    return (this.data?.cashCustomers ?? [])
      .filter(c => c.cashPending > 0)
      .sort((a, b) => this.ageDays(b) - this.ageDays(a));
  }

  /** The credit portion only — a customer may hold both kinds. */
  creditAmount(c: AgingExportCustomer): number {
    return c.totalOutstanding - c.cashPending;
  }

  totalCredit(): number {
    return this.creditRows.reduce((s, c) => s + this.creditAmount(c), 0);
  }

  totalCash(): number {
    return this.cashRows.reduce((s, c) => s + c.cashPending, 0);
  }

  billsOf(rows: AgingExportCustomer[]): number {
    return rows.reduce((s, c) => s + c.billCount, 0);
  }

  /** What is genuinely late, as opposed to merely open. */
  overdueTotal(rows: AgingExportCustomer[], cash: boolean): number {
    return rows
      .filter(c => this.overdueDays(c, cash) > 0)
      .reduce((s, c) => s + (cash ? c.cashPending : this.creditAmount(c)), 0);
  }
}
