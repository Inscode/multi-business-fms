import { Component, ChangeDetectionStrategy, ChangeDetectorRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { InvoiceReviewService, ReviewFilters, ReviewInvoice } from '../../core/services/invoice-review.service';

type StatusFilter = 'PENDING' | 'REVIEWED' | 'ALL';

/**
 * The admin's queue of invoices that have been entered but not yet looked at.
 *
 * Two things bring an invoice here: it is new, and — flagged separately — the
 * accountant pointed it at a different customer than the name it was billed under.
 */
@Component({
  selector: 'app-invoice-review',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, RouterLink,
            MatButtonModule, MatCheckboxModule, MatFormFieldModule, MatIconModule,
            MatInputModule, MatPaginatorModule, MatProgressSpinnerModule,
            MatSelectModule, MatTooltipModule],
  templateUrl: './invoice-review.component.html',
  styleUrl: './invoice-review.component.scss',
})
export class InvoiceReviewComponent implements OnInit {
  private service = inject(InvoiceReviewService);
  private snack   = inject(MatSnackBar);
  private cdr     = inject(ChangeDetectorRef);

  rows: ReviewInvoice[] = [];
  total = 0;
  page  = 0;
  size  = 25;
  loading = false;
  error = '';

  status: StatusFilter = 'PENDING';
  source: 'MANUAL' | 'IMPORT' | null = null;
  changedOnly = false;
  search = '';

  selected = new Set<number>();

  ngOnInit() { this.load(); }

  load() {
    this.loading = true;
    this.error = '';
    const f: ReviewFilters = {
      reviewed: this.status === 'ALL' ? null : this.status === 'REVIEWED',
      source: this.source,
      changedOnly: this.changedOnly,
      search: this.search,
      page: this.page,
      size: this.size,
    };
    this.service.list(f).subscribe({
      next: res => {
        this.rows = res.content ?? [];
        this.total = res.totalElements ?? 0;
        // A selection that survived a reload would let a later "mark reviewed" hit rows
        // the admin can no longer see, so it is dropped whenever the list changes.
        this.selected.clear();
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: err => {
        this.error = err?.error?.message ?? 'Could not load the review queue';
        this.loading = false;
        this.cdr.markForCheck();
      },
    });
  }

  onFilterChange() { this.page = 0; this.load(); }

  onPage(e: PageEvent) {
    this.page = e.pageIndex;
    this.size = e.pageSize;
    this.load();
  }

  clearFilters() {
    this.status = 'PENDING';
    this.source = null;
    this.changedOnly = false;
    this.search = '';
    this.onFilterChange();
  }

  // ── Selection ──────────────────────────────────────────────────────
  toggle(id: number, checked: boolean) {
    checked ? this.selected.add(id) : this.selected.delete(id);
    this.cdr.markForCheck();
  }

  isSelected(id: number) { return this.selected.has(id); }

  allSelected(): boolean {
    return this.rows.length > 0 && this.rows.every(r => this.selected.has(r.id));
  }

  someSelected(): boolean {
    return this.selected.size > 0 && !this.allSelected();
  }

  toggleAll(checked: boolean) {
    checked ? this.rows.forEach(r => this.selected.add(r.id))
            : this.rows.forEach(r => this.selected.delete(r.id));
    this.cdr.markForCheck();
  }

  // ── Actions ────────────────────────────────────────────────────────
  markOne(row: ReviewInvoice, reviewed: boolean) {
    this.service.setReviewed(row.id, reviewed).subscribe({
      next: () => {
        this.snack.open(reviewed ? `${row.invoiceNo} marked reviewed`
                                 : `${row.invoiceNo} reopened`, 'OK', { duration: 2500 });
        this.load();
      },
      error: err => this.snack.open(err?.error?.message ?? 'Failed', 'Dismiss', { duration: 4000 }),
    });
  }

  markSelected(reviewed: boolean) {
    const ids = [...this.selected];
    if (!ids.length) return;
    this.service.setReviewedBulk(ids, reviewed).subscribe({
      next: res => {
        this.snack.open(`${res.updated} invoice(s) marked ${reviewed ? 'reviewed' : 'pending'}`,
                        'OK', { duration: 2500 });
        this.load();
      },
      error: err => this.snack.open(err?.error?.message ?? 'Failed', 'Dismiss', { duration: 4000 }),
    });
  }

  // ── Display ────────────────────────────────────────────────────────
  /** Only worth showing when the printed name isn't the customer's own. */
  showsBilledName(r: ReviewInvoice): boolean {
    return !!r.billedName && r.billedName.trim().toLowerCase() !== r.customerName.trim().toLowerCase();
  }

  changedCount(): number { return this.rows.filter(r => r.customerChanged).length; }
  freeIssueCount(): number { return this.rows.filter(r => !!r.freeIssueAddedBy).length; }
}
