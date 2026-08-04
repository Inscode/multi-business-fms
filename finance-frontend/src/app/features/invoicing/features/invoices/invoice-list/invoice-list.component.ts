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

@Component({
  selector: 'app-invoice-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, RouterLink, MatButtonModule, MatIconModule,
            MatFormFieldModule, MatInputModule, MatSelectModule, MatProgressSpinnerModule],
  templateUrl: './invoice-list.component.html',
  styleUrl: './invoice-list.component.scss'
})
export class InvoiceListComponent implements OnInit {
  private svc = inject(InvoiceService);
  private cdr = inject(ChangeDetectorRef);

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
