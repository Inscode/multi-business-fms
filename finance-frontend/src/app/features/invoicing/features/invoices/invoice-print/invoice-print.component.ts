import { Component, OnInit, OnChanges, SimpleChanges, ChangeDetectionStrategy, ChangeDetectorRef, inject, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { InvoiceService } from '../../../core/services/invoice.service';
import { InvoicePrint } from '../../../core/models/models';

@Component({
  selector: 'app-invoice-print',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  templateUrl: './invoice-print.component.html',
  styleUrl: './invoice-print.component.scss'
})
export class InvoicePrintComponent implements OnInit, OnChanges {
  private route = inject(ActivatedRoute);
  private svc   = inject(InvoiceService);
  private cdr   = inject(ChangeDetectorRef);

  /** When embedded in another page, the host supplies the id instead of relying on the route. */
  @Input() invoiceId?: number;
  /** Embedded hosts control the print dialog themselves, so they can wait for data readiness first. */
  @Input() autoPrint = true;
  /** Fires once the invoice data has rendered, so an embedding host knows it's safe to call window.print(). */
  @Output() ready = new EventEmitter<void>();

  print: InvoicePrint | null = null;
  error = '';
  today = new Date();

  private loadedId?: number;

  ngOnInit(): void {
    const id = this.invoiceId ?? Number(this.route.snapshot.paramMap.get('id'));
    this.load(id);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['invoiceId'] && !changes['invoiceId'].firstChange && this.invoiceId !== this.loadedId) {
      this.load(this.invoiceId!);
    }
  }

  private load(id: number): void {
    this.loadedId = id;
    this.print = null;
    this.error = '';
    this.svc.print(id).subscribe({
      next: data => {
        this.print = data;
        this.cdr.markForCheck();
        if (this.autoPrint) {
          // Auto-trigger print dialog after render
          setTimeout(() => window.print(), 400);
        } else {
          setTimeout(() => this.ready.emit(), 400);
        }
      },
      error: () => {
        this.error = 'Failed to load invoice for printing.';
        this.cdr.markForCheck();
      }
    });
  }
}
