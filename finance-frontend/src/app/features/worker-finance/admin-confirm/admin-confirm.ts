import { Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTabsModule } from '@angular/material/tabs';
import { WorkerFinanceService, WorkerTabPurchaseResponse, WorkerAdvanceBonusResponse } from '../../../core/services/worker-finance';

@Component({
  selector: 'app-worker-finance-admin-confirm',
  standalone: true,
  imports: [
    CommonModule, DecimalPipe,
    MatButtonModule, MatCardModule, MatIconModule,
    MatProgressSpinnerModule, MatTabsModule,
  ],
  templateUrl: './admin-confirm.html',
  styleUrl: './admin-confirm.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WorkerFinanceAdminConfirm implements OnInit {
  tabPurchases: WorkerTabPurchaseResponse[] = [];
  advanceBonuses: WorkerAdvanceBonusResponse[] = [];
  loading = true;
  confirmingId: number | null = null;

  constructor(
    private workerFinanceService: WorkerFinanceService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    let loaded = 0;
    const done = () => { if (++loaded === 2) { this.loading = false; this.cdr.detectChanges(); } };

    this.workerFinanceService.getTabPurchasesPendingAdmin().subscribe({
      next: (r) => { this.tabPurchases = r; done(); },
      error: () => done(),
    });
    this.workerFinanceService.getAdvanceBonusPendingAdmin().subscribe({
      next: (r) => { this.advanceBonuses = r; done(); },
      error: () => done(),
    });
  }

  confirmTab(id: number): void {
    if (!confirm('Confirm payment for this tab purchase? This will mark it as PAID and make it deductible from salary.')) return;
    this.confirmingId = id;
    this.workerFinanceService.tabAdminConfirm(id).subscribe({
      next: () => { this.confirmingId = null; this.load(); },
      error: () => { this.confirmingId = null; this.cdr.detectChanges(); },
    });
  }

  confirmAb(id: number, type: string): void {
    const msg = type === 'ADVANCE'
      ? 'Confirm payment of this advance? It will be tracked as outstanding and deducted from salary.'
      : 'Confirm payment of this bonus? This will mark it as PAID (no salary deduction).';
    if (!confirm(msg)) return;
    this.confirmingId = id;
    this.workerFinanceService.abAdminConfirm(id).subscribe({
      next: () => { this.confirmingId = null; this.load(); },
      error: () => { this.confirmingId = null; this.cdr.detectChanges(); },
    });
  }

  get tabTotal(): number {
    return this.tabPurchases.reduce((s, t) => s + t.totalAmount, 0);
  }

  get abTotal(): number {
    return this.advanceBonuses.reduce((s, a) => s + a.amount, 0);
  }
}
