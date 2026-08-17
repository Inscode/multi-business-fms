import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatNativeDateModule } from '@angular/material/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ConfirmDialog } from '../../../../../shared/confirm-dialog/confirm-dialog';
import { localDateStr } from '../../../../../core/utils/date-utils';
import {
  StockTakeLine,
  StockTakePreview,
  StockTakeRow,
  StockTakeService,
} from '../../../../../core/services/stock-take';

/**
 * Writing a physical count over the system's stock figure.
 *
 * <p>For the case where system stock was never kept and the shelves are the only truth
 * there is. Nothing is written until the preview has been read: the counted figure
 * replaces the system's outright, so afterwards there is no balance left to reveal a
 * typo — a 5 entered where 50 was meant is invisible the moment it is applied.
 */
@Component({
  selector: 'app-stock-take',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatCheckboxModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatDividerModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatDialogModule,
    MatSnackBarModule,
  ],
  templateUrl: './stock-take.component.html',
  styleUrl: './stock-take.component.scss',
})
export class StockTakeComponent {

  reference = '';
  countedOn: Date = new Date();
  zeroUncounted = false;

  /** Pasted straight off a count sheet: code, quantity, optionally damage. */
  pasted = '';

  preview: StockTakePreview | null = null;
  loading = false;
  applying = false;
  error = '';
  applied = false;

  /** Rows worth looking at hide among matched ones, so matched can be folded away. */
  showMatched = false;
  showUncounted = false;

  constructor(
    private service: StockTakeService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
    private cdr: ChangeDetectorRef,
  ) {}

  /**
   * Turns pasted text into lines.
   *
   * <p>Accepts tabs, commas or runs of spaces between the columns, because the text
   * arrives from a spreadsheet, a phone note or a typed list depending on who counted,
   * and rejecting a separator would just mean somebody reformats by hand.
   */
  private parse(): StockTakeLine[] {
    const lines: StockTakeLine[] = [];
    for (const raw of this.pasted.split(/\r?\n/)) {
      const line = raw.trim();
      if (!line) continue;
      const parts = line.split(/[\t,]|\s{2,}|\s+(?=\d+\s*$)|\s+/).filter(p => p !== '');
      if (parts.length < 2) continue;

      const code = parts[0];
      const qty = Number(parts[1]);
      const damage = parts.length > 2 ? Number(parts[2]) : undefined;
      if (!Number.isFinite(qty)) continue;

      lines.push({
        itemCode: code,
        countedQty: qty,
        countedDamageQty: Number.isFinite(damage as number) ? damage : undefined,
      });
    }
    return lines;
  }

  get parsedCount(): number { return this.parse().length; }

  get canPreview(): boolean {
    return !!this.reference.trim() && this.parsedCount > 0 && !this.loading;
  }

  runPreview(): void {
    if (!this.canPreview) return;
    this.loading = true;
    this.error = '';
    this.applied = false;
    this.service.preview({
      reference: this.reference.trim(),
      countedOn: localDateStr(this.countedOn),
      lines: this.parse(),
      zeroUncounted: this.zeroUncounted,
    }).subscribe({
      next: (p) => {
        this.preview = p;
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Could not work out the count.';
        this.loading = false;
        this.cdr.markForCheck();
      },
    });
  }

  /** Rows that would change something, plus anything flagged. */
  get changedRows(): StockTakeRow[] {
    const rows = this.preview?.rows ?? [];
    return this.showMatched ? rows : rows.filter(r => r.status !== 'MATCHED');
  }

  get uncountedWithStock(): StockTakeRow[] {
    return (this.preview?.uncounted ?? []).filter(r => (r.systemQty ?? 0) !== 0);
  }

  get problemCount(): number {
    return (this.preview?.rows ?? []).filter(r => !!r.warning).length;
  }

  statusClass(status: string): string {
    switch (status) {
      case 'INCREASE':  return 's-up';
      case 'DECREASE':  return 's-down';
      case 'MATCHED':   return 's-match';
      case 'NOT_FOUND': return 's-error';
      case 'DUPLICATE': return 's-error';
      default:          return '';
    }
  }

  apply(): void {
    if (!this.preview) return;
    const p = this.preview;

    if (p.notFoundCount > 0) {
      this.snackBar.open(
        `${p.notFoundCount} code${p.notFoundCount === 1 ? '' : 's'} match no item. `
        + 'Fix those lines first.', 'OK', { duration: 6000 });
      return;
    }

    const zeroing = this.zeroUncounted ? this.uncountedWithStock.length : 0;
    const message =
      `${p.changedCount} item${p.changedCount === 1 ? '' : 's'} will be set to the counted `
      + `figure, a net change of ${p.netUnitChange > 0 ? '+' : ''}${p.netUnitChange} units.`
      + (zeroing
          ? `\n\n${zeroing} item${zeroing === 1 ? '' : 's'} not on the sheet will be set to zero.`
          : '')
      + '\n\nThis replaces the system figure outright. There is no balance afterwards to '
      + 'show a wrong number, so check the list before continuing.';

    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Apply this count?',
        message,
        confirmText: 'Apply count',
        confirmColor: 'primary',
      },
      maxWidth: '95vw',
    }).afterClosed().subscribe(result => {
      if (!result?.confirmed) return;
      this.applying = true;
      this.cdr.markForCheck();

      this.service.apply({
        reference: this.reference.trim(),
        countedOn: localDateStr(this.countedOn),
        lines: this.parse(),
        zeroUncounted: this.zeroUncounted,
      }).subscribe({
        next: () => {
          this.applying = false;
          this.applied = true;
          this.snackBar.open('Count applied. Stock now matches the shelves.', 'OK',
                             { duration: 5000 });
          this.cdr.markForCheck();
        },
        error: (err) => {
          this.applying = false;
          this.error = err?.error?.message ?? 'Could not apply the count.';
          this.cdr.markForCheck();
        },
      });
    });
  }

  reset(): void {
    this.preview = null;
    this.applied = false;
    this.error = '';
    this.pasted = '';
    this.cdr.markForCheck();
  }
}
