import { Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTabsModule } from '@angular/material/tabs';
import { WorkerFinanceService, WorkerTabPurchaseResponse, WorkerAdvanceBonusResponse } from '../../../core/services/worker-finance';

@Component({
  selector: 'app-worker-finance-owner-review',
  standalone: true,
  imports: [
    CommonModule, DecimalPipe,
    MatButtonModule, MatCardModule, MatIconModule,
    MatProgressSpinnerModule, MatTabsModule,
  ],
  templateUrl: './owner-review.html',
  styleUrl: './owner-review.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WorkerFinanceOwnerReview implements OnInit {
  tabPurchases: WorkerTabPurchaseResponse[] = [];
  advanceBonuses: WorkerAdvanceBonusResponse[] = [];
  loading = true;

  constructor(
    private workerFinanceService: WorkerFinanceService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.cdr.detectChanges();
    let loaded = 0;
    const done = () => { if (++loaded === 2) { this.loading = false; this.cdr.detectChanges(); } };

    this.workerFinanceService.getTabPurchasesPendingOwner().subscribe({
      next: (r) => { this.tabPurchases = r; done(); },
      error: () => { this.tabPurchases = []; done(); },
    });
    this.workerFinanceService.getAdvanceBonusPendingOwner().subscribe({
      next: (r) => { this.advanceBonuses = r; done(); },
      error: () => { this.advanceBonuses = []; done(); },
    });
  }

  approveTab(id: number): void {
    this.workerFinanceService.tabOwnerApprove(id).subscribe({
      next: () => this.load(),
      error: (err) => alert(err?.error?.message ?? 'Failed to approve.'),
    });
  }

  rejectTab(id: number): void {
    const reason = prompt('Rejection reason (optional):') ?? '';
    this.workerFinanceService.tabOwnerReject(id, reason).subscribe({
      next: () => this.load(),
      error: () => alert('Failed to reject.'),
    });
  }

  approveAb(id: number): void {
    this.workerFinanceService.abOwnerApprove(id).subscribe({
      next: () => this.load(),
      error: (err) => alert(err?.error?.message ?? 'Failed to approve.'),
    });
  }

  rejectAb(id: number): void {
    const reason = prompt('Rejection reason (optional):') ?? '';
    this.workerFinanceService.abOwnerReject(id, reason).subscribe({
      next: () => this.load(),
      error: () => alert('Failed to reject.'),
    });
  }

  get tabTotal(): number {
    return this.tabPurchases.reduce((s, t) => s + t.totalAmount, 0);
  }

  get abTotal(): number {
    return this.advanceBonuses.reduce((s, a) => s + a.amount, 0);
  }
}
