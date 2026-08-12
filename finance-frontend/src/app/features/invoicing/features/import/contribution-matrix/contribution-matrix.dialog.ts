import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';

/** One invoice in the load, as a column of the grid. */
export interface MatrixInvoice {
  invoiceNo?: string;
  /** Who the bill is actually for — the number alone rarely says. */
  customerName?: string | null;
  lines: { itemCode?: string; qty?: number; freeQty?: number }[];
}

export interface ContributionMatrixData {
  companyName: string;
  /** Areas, dates — whatever names this particular load. */
  scope?: string;
  invoices: MatrixInvoice[];
  /** Count the free quantities alongside the paid ones. */
  includeFree?: boolean;
}

interface MatrixRow {
  code: string;
  cells: number[];
  total: number;
}

/**
 * Item quantity by bill, as a grid: items down the side, invoices across the top.
 *
 * <p>This is the shape the agent's summary bill comes in, so it is the shape that can
 * be checked against it — a per-item total alone says the load is right in aggregate
 * but not which invoice a discrepancy came from. Reading down a column reconciles one
 * bill; reading across a row reconciles one item.
 *
 * <p>Built entirely from what is already parsed on screen, so it costs no round trip
 * and works before anything is imported — which is the only moment it is useful.
 */
@Component({
  selector: 'app-contribution-matrix',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatIconModule],
  templateUrl: './contribution-matrix.dialog.html',
  styleUrl: './contribution-matrix.dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContributionMatrixDialog {
  readonly columns: string[] = [];
  /** Customer per column, in the same order — shown under each bill number. */
  readonly columnNames: string[] = [];
  readonly rows: MatrixRow[] = [];
  readonly columnTotals: number[] = [];
  readonly grandTotal: number;
  readonly generatedOn = new Date();

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: ContributionMatrixData,
    private ref: MatDialogRef<ContributionMatrixDialog>,
  ) {
    const invoices = (data.invoices ?? []).filter(i => !!i.invoiceNo);
    this.columns = invoices.map(i => i.invoiceNo!);
    this.columnNames = invoices.map(i => (i.customerName ?? '').trim());

    // code -> quantity per column, built in one pass over the parsed lines
    const byCode = new Map<string, number[]>();
    invoices.forEach((inv, col) => {
      for (const line of inv.lines ?? []) {
        const code = line.itemCode || '—';
        const qty = (line.qty ?? 0) + (data.includeFree ? (line.freeQty ?? 0) : 0);
        if (!qty) continue;
        const cells = byCode.get(code) ?? new Array(invoices.length).fill(0);
        cells[col] += qty;
        byCode.set(code, cells);
      }
    });

    // Heaviest items first: a discrepancy in a big line is what matters, and it puts
    // the numbers worth checking at the top of the page.
    this.rows = [...byCode.entries()]
      .map(([code, cells]) => ({ code, cells, total: cells.reduce((s, n) => s + n, 0) }))
      .sort((a, b) => b.total - a.total || a.code.localeCompare(b.code));

    this.columnTotals = this.columns.map((_, col) =>
      this.rows.reduce((s, r) => s + r.cells[col], 0));
    this.grandTotal = this.columnTotals.reduce((s, n) => s + n, 0);
  }

  /** Strips the agent's prefix so the rotated headings stay narrow. */
  shortRef(ref: string): string {
    const slash = ref.lastIndexOf('/');
    return slash >= 0 ? ref.slice(slash + 1) : ref;
  }

  /**
   * Prints the grid alone. The dialog lives in the CDK overlay, a sibling of the app
   * root, so without this the import screen prints behind it. The body class is set
   * only for the duration of the print and taken off again afterwards.
   */
  print(): void {
    document.body.classList.add('matrix-print');
    const cleanup = () => {
      document.body.classList.remove('matrix-print');
      window.removeEventListener('afterprint', cleanup);
    };
    window.addEventListener('afterprint', cleanup);
    window.print();
    // Safari never fires afterprint in some versions; this makes sure the class goes.
    setTimeout(cleanup, 1000);
  }

  close(): void { this.ref.close(); }

  /**
   * Downloads the grid as CSV, so it can be put beside the agent's own sheet in a
   * spreadsheet rather than compared by eye.
   */
  downloadCsv(): void {
    const esc = (v: string | number) => {
      const s = String(v);
      return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
    };

    const lines: string[] = [];
    // Same short refs as the grid, so the file and the screen read alike, with the
    // customers on a second row so a column can be identified in a spreadsheet too.
    lines.push(['Item', ...this.columns.map(c => this.shortRef(c)), 'Total'].map(esc).join(','));
    lines.push(['Customer', ...this.columnNames, ''].map(esc).join(','));
    for (const r of this.rows) {
      lines.push([r.code, ...r.cells, r.total].map(esc).join(','));
    }
    lines.push(['Total', ...this.columnTotals, this.grandTotal].map(esc).join(','));

    // BOM so Excel opens it as UTF-8 rather than mangling the item descriptions.
    const blob = new Blob(['﻿' + lines.join('\r\n')],
                          { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `item-contribution-${this.generatedOn.toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }
}
