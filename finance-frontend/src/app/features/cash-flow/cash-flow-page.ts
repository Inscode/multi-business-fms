import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  CashFlowEntry, CashFlowForecast, CashFlowService, SupplierPayable,
} from '../../core/services/cash-flow';
import { ConfirmDialog } from '../../shared/confirm-dialog/confirm-dialog';

/**
 * Forward cash-flow: over the next N days, what is committed to arrive against
 * what falls due to the principals — per business, so you can see which one is
 * carrying itself and which is not.
 *
 * Only money still to come is counted. Cash already banked is history and would
 * flatter the picture; outstanding balances with no collection date are shown
 * separately rather than treated as certain.
 */
@Component({
  selector: 'app-cash-flow-page',
  standalone: true,
  imports: [
    CommonModule, FormsModule, DatePipe, DecimalPipe,
    MatButtonModule, MatIconModule, MatSelectModule, MatFormFieldModule,
    MatInputModule, MatProgressSpinnerModule, MatTabsModule, MatDialogModule, MatTooltipModule,
  ],
  templateUrl: './cash-flow-page.html',
  styleUrl: './cash-flow-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CashFlowPage implements OnInit {
  forecast: CashFlowForecast | null = null;
  entries: CashFlowEntry[] = [];
  payables: SupplierPayable[] = [];

  loading = true;
  error = '';
  saving = false;

  horizonDays = 70;
  horizons = [30, 45, 60, 70, 90, 120, 180];

  // Entry form for obligations this system has no GRN for
  showPayableForm = false;
  businesses = ['RAINCO', 'STATIONERY', 'PLASTIC', 'RETAIL_SHOP', 'HARDWARE', 'MIX'];
  newPayable = this.blankPayable();

  showSettled = false;

  constructor(
    private service: CashFlowService,
    private dialog: MatDialog,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.error = '';
    this.cdr.markForCheck();

    this.service.forecast(this.horizonDays).subscribe({
      next: (f) => {
        this.forecast = f;
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: () => { this.error = 'Could not load the forecast.'; this.loading = false; this.cdr.markForCheck(); },
    });

    this.service.entries(this.horizonDays).subscribe({
      next: (e) => { this.entries = e; this.cdr.markForCheck(); },
      error: () => {},
    });

    this.service.listPayables().subscribe({
      next: (p) => { this.payables = p; this.cdr.markForCheck(); },
      error: () => {},
    });
  }

  onHorizonChange(): void { this.load(); }

  // ── Forecast reading ────────────────────────────────────────────

  /** Green when the money coming in covers what falls due. */
  netClass(net: number): string {
    return net >= 0 ? 'pos' : 'neg';
  }

  get visibleEntries(): CashFlowEntry[] {
    return this.entries;
  }

  // ── Obligations without a GRN ───────────────────────────────────

  private blankPayable() {
    return {
      business: 'RAINCO',
      supplierName: '',
      description: '',
      amount: null as number | null,
      dueDate: '',
      chequeNumber: '',
      bankName: '',
      notes: '',
    };
  }

  openPayableForm(): void {
    this.newPayable = this.blankPayable();
    this.showPayableForm = true;
    this.cdr.markForCheck();
  }

  cancelPayableForm(): void { this.showPayableForm = false; this.cdr.markForCheck(); }

  get payableValid(): boolean {
    const p = this.newPayable;
    return !!p.business && !!p.description.trim() && !!p.dueDate
        && p.amount != null && p.amount > 0;
  }

  savePayable(): void {
    if (!this.payableValid) return;
    this.saving = true;
    this.error = '';
    this.cdr.markForCheck();

    const p = this.newPayable;
    this.service.createPayable({
      business: p.business,
      supplierName: p.supplierName || undefined,
      description: p.description,
      amount: p.amount!,
      dueDate: p.dueDate,
      chequeNumber: p.chequeNumber || undefined,
      bankName: p.bankName || undefined,
      notes: p.notes || undefined,
    }).subscribe({
      next: () => { this.saving = false; this.showPayableForm = false; this.load(); },
      error: (e) => {
        this.error = e?.error?.message ?? 'Could not save that commitment.';
        this.saving = false;
        this.cdr.markForCheck();
      },
    });
  }

  toggleSettled(p: SupplierPayable): void {
    this.service.settlePayable(p.id, !p.settled).subscribe({
      next: () => this.load(),
      error: () => {},
    });
  }

  deletePayable(p: SupplierPayable): void {
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Delete commitment',
        message: `Delete "${p.description}" (Rs ${p.amount.toLocaleString()})? It will stop counting against your forecast.`,
        confirmText: 'Delete',
        confirmColor: 'warn',
      },
      maxWidth: '95vw',
    }).afterClosed().subscribe(result => {
      if (!result?.confirmed) return;
      this.service.deletePayable(p.id).subscribe({ next: () => this.load() });
    });
  }

  get visiblePayables(): SupplierPayable[] {
    return this.showSettled ? this.payables : this.payables.filter(p => !p.settled);
  }

  get unsettledTotal(): number {
    return this.payables.filter(p => !p.settled).reduce((s, p) => s + p.amount, 0);
  }

  dueClass(days: number): string {
    if (days < 0) return 'overdue';
    if (days <= 7) return 'soon';
    return '';
  }
}
