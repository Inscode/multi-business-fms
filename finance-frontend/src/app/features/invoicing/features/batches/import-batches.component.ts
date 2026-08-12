import { Component, ChangeDetectionStrategy, ChangeDetectorRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { environment } from '../../../../../environments/environment';

interface BatchInvoice {
  id: number;
  invoiceNo: string;
  externalRef?: string | null;
  customerName: string;
  netTotal: number;
  included: boolean;
}

interface ProductLine {
  itemId: number;
  itemCode: string;
  description: string;
  brandName: string;
  qty: number;
  freeQty: number;
  value: number;
}

interface ImportBatch {
  id: number;
  category: string;
  fileName?: string | null;
  importedBy?: string | null;
  importedAt: string;
  invoiceCount: number;
  invoices?: BatchInvoice[];
  products?: ProductLine[];
  totalQty?: number;
  totalFreeQty?: number;
  totalValue?: number;
}

/**
 * Product totals for one import run, to check against the agent's summary bill.
 *
 * Invoices start included and can be unticked — the agent's summary often covers only
 * part of an upload, and unticking changes only what is counted, never the data.
 */
@Component({
  selector: 'app-import-batches',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, MatButtonModule, MatCheckboxModule,
            MatIconModule, MatProgressSpinnerModule, MatTooltipModule],
  templateUrl: './import-batches.component.html',
  styleUrl: './import-batches.component.scss',
})
export class ImportBatchesComponent implements OnInit {
  private http = inject(HttpClient);
  private cdr  = inject(ChangeDetectorRef);
  private base = `${environment.apiUrl}/invoicing/import`;

  batches: ImportBatch[] = [];
  selected: ImportBatch | null = null;
  excluded = new Set<number>();
  loading = false;
  loadingSummary = false;
  error = '';

  ngOnInit() { this.loadBatches(); }

  loadBatches() {
    this.loading = true;
    this.http.get<ImportBatch[]>(`${this.base}/batches`).subscribe({
      next: res => {
        this.batches = res ?? [];
        this.loading = false;
        if (this.batches.length && !this.selected) this.open(this.batches[0]);
        this.cdr.markForCheck();
      },
      error: err => {
        this.error = err?.error?.message ?? 'Could not load import batches';
        this.loading = false;
        this.cdr.markForCheck();
      },
    });
  }

  open(batch: ImportBatch) {
    this.excluded.clear();
    this.loadSummary(batch.id);
  }

  private loadSummary(id: number) {
    this.loadingSummary = true;
    let params = new HttpParams();
    for (const ex of this.excluded) params = params.append('exclude', ex);
    this.http.get<ImportBatch>(`${this.base}/batches/${id}/summary`, { params }).subscribe({
      next: res => {
        this.selected = res;
        this.loadingSummary = false;
        this.cdr.markForCheck();
      },
      error: err => {
        this.error = err?.error?.message ?? 'Could not load the summary';
        this.loadingSummary = false;
        this.cdr.markForCheck();
      },
    });
  }

  toggleInvoice(inv: BatchInvoice, included: boolean) {
    included ? this.excluded.delete(inv.id) : this.excluded.add(inv.id);
    if (this.selected) this.loadSummary(this.selected.id);
  }

  includeAll() {
    this.excluded.clear();
    if (this.selected) this.loadSummary(this.selected.id);
  }

  includedCount(): number {
    return (this.selected?.invoices ?? []).filter(i => i.included).length;
  }

  isSelected(b: ImportBatch): boolean { return this.selected?.id === b.id; }
}
