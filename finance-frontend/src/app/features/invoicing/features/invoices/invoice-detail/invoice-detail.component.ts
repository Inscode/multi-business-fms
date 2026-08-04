import { Component, ChangeDetectionStrategy, ChangeDetectorRef, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { catchError, of } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { InvoiceService } from '../../../core/services/invoice.service';
import { Invoice } from '../../../core/models/models';
import { InvoicePrintComponent } from '../invoice-print/invoice-print.component';

@Component({
  selector: 'app-invoice-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule,
            MatButtonModule, MatIconModule, MatProgressSpinnerModule, MatTooltipModule,
            InvoicePrintComponent],
  templateUrl: './invoice-detail.component.html',
  styleUrl: './invoice-detail.component.scss'
})
export class InvoiceDetailComponent implements OnInit {
  private route  = inject(ActivatedRoute);
  private svc    = inject(InvoiceService);
  private router = inject(Router);
  private cdr    = inject(ChangeDetectorRef);

  invoice: Invoice | null = null;
  loading = true;
  error   = '';

  printReady = false;
  printRequested = false;

  ngOnInit() {
    this.route.paramMap.pipe(
      switchMap(p => {
        const id = Number(p.get('id'));
        return this.svc.getById(id).pipe(catchError(err => {
          this.error = err?.error?.message ?? 'Invoice not found';
          return of(null);
        }));
      })
    ).subscribe(inv => {
      this.invoice = inv;
      this.loading = false;
      this.printReady = false;
      this.printRequested = false;
      this.cdr.markForCheck();
    });
  }

  print() {
    if (!this.invoice) return;
    if (this.printReady) {
      window.print();
    } else {
      this.printRequested = true;
    }
  }

  onPrintReady() {
    this.printReady = true;
    if (this.printRequested) {
      this.printRequested = false;
      window.print();
    }
  }

  back() { this.router.navigate(['/invoicing/invoices']); }

  methodLabel(m: string) {
    return ({ MIX: 'Mix (Rainco + Stationery)', RAINCO_ONLY: 'Rainco Only', STATIONERY_ONLY: 'Stationery Only' } as any)[m] ?? m;
  }

  methodClass(m: string) {
    return ({ MIX: 'info', RAINCO_ONLY: 'success', STATIONERY_ONLY: 'warn' } as any)[m] ?? 'info';
  }
}
