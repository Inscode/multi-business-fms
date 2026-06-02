import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTabsModule } from '@angular/material/tabs';
import { SalaryPaymentResponse, SalaryService } from '../../../core/services/salary';
import { WorkerAdvanceBonusResponse, WorkerFinanceService, WorkerTabPurchaseResponse } from '../../../core/services/worker-finance';

@Component({
  selector: 'app-salary-review',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatTabsModule,
  ],
  templateUrl: './salary-review.html',
  styleUrl: './salary-review.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SalaryReview implements OnInit {
  payments: SalaryPaymentResponse[] = [];
  tabPurchases: WorkerTabPurchaseResponse[] = [];
  advanceBonuses: WorkerAdvanceBonusResponse[] = [];
  loading = false;
  error = false;

  // Default: current month from first day to today
  fromDate = new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().split('T')[0];
  toDate = new Date().toISOString().split('T')[0];
  filterStatus = '';

  get displayedPayments(): SalaryPaymentResponse[] {
    return this.payments.filter(p => !this.filterStatus || p.status === this.filterStatus);
  }

  get displayedPurchases(): WorkerTabPurchaseResponse[] {
    return this.tabPurchases;
  }

  get displayedAdvanceBonuses(): WorkerAdvanceBonusResponse[] {
    return this.advanceBonuses;
  }

  get pendingCount(): number {
    return this.payments.filter(p => p.status === 'PENDING_APPROVAL').length;
  }

  get totalSalaryPaid(): number {
    return this.payments
      .filter(p => p.status === 'RECORDED' || p.status === 'APPROVED')
      .reduce((s, p) => s + p.amount, 0);
  }

  get totalTabPurchases(): number {
    return this.tabPurchases.reduce((s, t) => s + t.totalAmount, 0);
  }

  get totalAdvances(): number {
    return this.advanceBonuses.filter(a => a.type === 'ADVANCE').reduce((s, a) => s + a.amount, 0);
  }

  get totalBonuses(): number {
    return this.advanceBonuses.filter(a => a.type === 'BONUS').reduce((s, a) => s + a.amount, 0);
  }

  constructor(
    private salaryService: SalaryService,
    private workerFinanceService: WorkerFinanceService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    if (!this.fromDate || !this.toDate) return;
    this.loading = true;
    this.error = false;
    let loaded = 0;
    const done = (ok: boolean) => {
      if (!ok) this.error = true;
      if (++loaded === 3) { this.loading = false; this.cdr.detectChanges(); }
    };

    this.salaryService.getPaymentsByDateRange(this.fromDate, this.toDate).subscribe({
      next: (p) => { this.payments = p; done(true); },
      error: () => done(false),
    });

    this.workerFinanceService.getTabPurchasesByDateRange(this.fromDate, this.toDate).subscribe({
      next: (list) => { this.tabPurchases = list; done(true); },
      error: () => { this.tabPurchases = []; done(true); },
    });

    this.workerFinanceService.getAdvanceBonusByDateRange(this.fromDate, this.toDate).subscribe({
      next: (list) => { this.advanceBonuses = list; done(true); },
      error: () => { this.advanceBonuses = []; done(true); },
    });
  }

  approve(p: SalaryPaymentResponse): void {
    const overMsg = p.overSalary ? `\n\n⚠️ WARNING: This will exceed ${p.recipientName}'s monthly salary of Rs ${p.monthlySalary?.toLocaleString()}.` : '';
    if (!confirm(`Approve Rs ${p.amount.toLocaleString()} for ${p.recipientName} (${p.month})?${overMsg}`)) return;
    this.salaryService.approve(p.id).subscribe({
      next: () => this.load(),
      error: (err) => alert(err?.error?.message ?? 'Failed to approve.'),
    });
  }

  reject(p: SalaryPaymentResponse): void {
    const reason = prompt('Rejection reason (optional):') ?? '';
    this.salaryService.reject(p.id, reason).subscribe({
      next: () => this.load(),
      error: () => alert('Failed to reject.'),
    });
  }

  statusLabel(s: string): string {
    const map: Record<string, string> = {
      RECORDED: 'Recorded', PENDING_APPROVAL: 'Pending', APPROVED: 'Approved', REJECTED: 'Rejected',
    };
    return map[s] ?? s;
  }

  wfStatusLabel(s: string): string {
    const map: Record<string, string> = {
      PENDING_OWNER: 'Pending Owner', OWNER_APPROVED: 'Owner Approved', PAID: 'Paid', REJECTED: 'Rejected',
    };
    return map[s] ?? s;
  }
}
