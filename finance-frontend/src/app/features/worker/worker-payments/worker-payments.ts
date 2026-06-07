import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { WorkerPortalService, WorkerPaymentEntry } from '../../../core/services/worker-portal';
import { WorkerTranslateService } from '../../../core/services/worker-translate';

@Component({
  selector: 'app-worker-payments',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MatIconModule, MatProgressSpinnerModule, DecimalPipe],
  template: `
<div class="wp-page">
  <div class="wp-header">{{ tr.t('your_entries') }}</div>

  <div class="w-center" *ngIf="loading">
    <mat-progress-spinner mode="indeterminate" diameter="32"></mat-progress-spinner>
  </div>

  <div class="w-center" *ngIf="!loading && entries.length === 0">
    <mat-icon style="font-size:48px;color:#e0e0e0">payments</mat-icon>
    <p style="color:#9e9e9e;margin:8px 0 0">{{ tr.t('no_payments') }}</p>
  </div>

  <!-- Summary totals -->
  <div class="summary-bar" *ngIf="entries.length > 0">
    <div class="sum-item">
      <span class="sum-label">💵 Cash</span>
      <span class="sum-val">Rs {{ cashTotal | number }}</span>
    </div>
    <div class="sum-item">
      <span class="sum-label">🧾 Cheque</span>
      <span class="sum-val">Rs {{ chequeTotal | number }}</span>
    </div>
    <div class="sum-item total">
      <span class="sum-label">Total</span>
      <span class="sum-val">Rs {{ grandTotal | number }}</span>
    </div>
  </div>

  <div class="wp-list">
    <div class="wp-entry" *ngFor="let e of entries">
      <div class="wpe-row">
        <span class="wpe-bill">{{ e.billNumber }}</span>
        <span class="wpe-type-chip" [class.cash]="e.paymentType === 'CASH'" [class.cheque]="e.paymentType === 'CHEQUE'">
          {{ e.paymentType }}
        </span>
        <span class="wpe-status" [class.pending]="e.status === 'PENDING'" [class.confirmed]="e.status === 'OWNER_CONFIRMED'" [class.rejected]="e.status === 'REJECTED'">
          {{ tr.t(e.status.toLowerCase()) }}
        </span>
      </div>
      <div class="wpe-main-row">
        <span class="wpe-customer">{{ e.customerName }}</span>
        <span class="wpe-amt">Rs {{ e.amount | number }}</span>
      </div>
      <div class="wpe-cheque" *ngIf="e.chequeNumber">
        {{ e.chequeNumber }} · {{ e.bankName }}
      </div>
      <div class="wpe-note" *ngIf="e.workerNote">💬 {{ e.workerNote }}</div>
    </div>
  </div>
</div>
  `,
  styleUrl: './worker-payments.scss',
})
export class WorkerPayments implements OnInit {
  entries: WorkerPaymentEntry[] = [];
  loading = false;

  get cashTotal(): number   { return this.entries.filter(e => e.paymentType === 'CASH').reduce((s, e) => s + e.amount, 0); }
  get chequeTotal(): number { return this.entries.filter(e => e.paymentType === 'CHEQUE').reduce((s, e) => s + e.amount, 0); }
  get grandTotal(): number  { return this.entries.reduce((s, e) => s + e.amount, 0); }

  constructor(
    public tr: WorkerTranslateService,
    private svc: WorkerPortalService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.loading = true;
    this.svc.getTodayPayments().subscribe({
      next: e => { this.entries = e; this.loading = false; this.cdr.detectChanges(); },
      error: () => { this.loading = false; this.cdr.detectChanges(); },
    });
  }
}
