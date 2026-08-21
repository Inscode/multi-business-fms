import { Component, ChangeDetectionStrategy, ChangeDetectorRef, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Subject, debounceTime, distinctUntilChanged, switchMap, catchError, of } from 'rxjs';
import { InvoiceService } from '../../../core/services/invoice.service';
import { InvoiceSummary } from '../../../core/models/models';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Auth } from '../../../../../core/services/auth';
import { ConfirmDialog } from '../../../../../shared/confirm-dialog/confirm-dialog';

@Component({
  selector: 'app-invoice-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, RouterLink, MatButtonModule, MatIconModule,
            MatFormFieldModule, MatInputModule, MatSelectModule, MatProgressSpinnerModule,
            MatDialogModule, MatSnackBarModule, MatTooltipModule],
  templateUrl: './invoice-list.component.html',
  styleUrl: './invoice-list.component.scss'
})
export class InvoiceListComponent implements OnInit {
  private svc = inject(InvoiceService);
  private cdr = inject(ChangeDetectorRef);
  private dialog = inject(MatDialog);
  private snackBar = inject(MatSnackBar);
  private auth = inject(Auth);

  /** Voiding an invoice moves stock and cancels a bill; not a clerical decision. */
  get isAdmin(): boolean { return this.auth.getRole() === 'ADMIN'; }

  /**
   * Voids the invoice and the bill it raised, and returns the goods to stock.
   *
   * <p>A reason is required: the invoice keeps its number and stays in the list, so this
   * is the only thing that will explain it to whoever finds it later.
   */
  cancelInvoice(inv: InvoiceSummary): void {
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Cancel ' + inv.invoiceNo,
        message:
          `The goods go back into stock and bill ${inv.invoiceNo} is cancelled with it `
          + 'in the bills section.\n\nThe invoice itself is kept under its number — it '
          + 'was issued, and the stock moved, so the record of that stays.\n\n'
          + 'If money has been collected on that bill, nothing is changed at all.',
        confirmText: 'Cancel invoice',
        confirmColor: 'warn',
        showInput: true,
        inputLabel: 'Reason (required)',
      },
      maxWidth: '95vw',
    }).afterClosed().subscribe(result => {
      if (!result?.confirmed) return;
      const reason = String(result.inputValue ?? '').trim();
      if (!reason) {
        this.snackBar.open('A reason is needed to cancel an invoice.', 'OK',
                           { duration: 4000 });
        return;
      }
      this.svc.cancel(inv.id, reason).subscribe({
        next: () => {
          this.snackBar.open('Cancelled, and the stock is back.', 'OK', { duration: 4000 });
          this.load();
        },
        error: (err) => this.snackBar.open(
          err?.error?.message ?? 'Could not cancel the invoice.', 'OK', { duration: 6000 }),
      });
    });
  }

  /** Removes the invoice here only; the bill in the bills section is left alone. */
  deleteInvoice(inv: InvoiceSummary): void {
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Delete ' + inv.invoiceNo,
        message:
          'The invoice is removed from this section and its goods go back into stock.'
          + '\n\nThe bill in the bills section is left exactly as it is — it may have '
          + 'been entered by hand before this invoice existed, and may already be '
          + 'collecting money. Cancel instead if you want the record kept.',
        confirmText: 'Delete invoice',
        confirmColor: 'warn',
      },
      maxWidth: '95vw',
    }).afterClosed().subscribe(result => {
      if (!result?.confirmed) return;
      this.svc.delete(inv.id).subscribe({
        next: () => {
          this.snackBar.open('Deleted, and the stock is back.', 'OK', { duration: 4000 });
          this.load();
        },
        error: (err) => this.snackBar.open(
          err?.error?.message ?? 'Could not delete the invoice.', 'OK', { duration: 6000 }),
      });
    });
  }

  invoices: InvoiceSummary[] = [];
  loading = true;
  search = '';
  method = '';
  page = 0;
  totalPages = 0;
  totalElements = 0;

  private search$ = new Subject<string>();

  ngOnInit() {
    this.search$.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap(q => {
        this.loading = true;
        this.cdr.markForCheck();
        return this.svc.search({ search: q || undefined, method: this.method || undefined, page: 0 }).pipe(
          catchError(() => of(null))
        );
      })
    ).subscribe(res => {
      if (res) {
        this.invoices = res.content;
        this.totalPages = res.totalPages;
        this.totalElements = res.totalElements;
        this.page = 0;
      }
      this.loading = false;
      this.cdr.markForCheck();
    });

    this.load();
  }

  load() {
    this.loading = true;
    this.svc.search({ search: this.search || undefined, method: this.method || undefined, page: this.page })
      .pipe(catchError(() => of(null)))
      .subscribe(res => {
        if (res) {
          this.invoices = res.content;
          this.totalPages = res.totalPages;
          this.totalElements = res.totalElements;
        }
        this.loading = false;
        this.cdr.markForCheck();
      });
  }

  onSearch(val: string) {
    this.search = val;
    this.search$.next(val);
  }

  onMethodChange() {
    this.page = 0;
    this.load();
  }

  prevPage() { if (this.page > 0) { this.page--; this.load(); } }
  nextPage() { if (this.page < this.totalPages - 1) { this.page++; this.load(); } }

  methodLabel(m: string) {
    return ({ MIX: 'Mix', RAINCO_ONLY: 'Rainco', STATIONERY_ONLY: 'Stationery' } as any)[m] ?? m;
  }

  methodClass(m: string) {
    return ({ MIX: 'info', RAINCO_ONLY: 'success', STATIONERY_ONLY: 'warn' } as any)[m] ?? 'info';
  }

  typeClass(t: string) {
    return t === 'CASH' ? 'success' : 'info';
  }
}
