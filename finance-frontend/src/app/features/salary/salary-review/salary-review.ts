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
  pendingApprovals: SalaryPaymentResponse[] = [];
  tabPurchases: WorkerTabPurchaseResponse[] = [];
  advanceBonuses: WorkerAdvanceBonusResponse[] = [];

  salaryLoading = false;
  othersLoading = false;
  salaryError   = false;

  // Salary Money tab filters
  selectedMonth = new Date().toISOString().slice(0, 7); // "2026-07"
  filterStatus  = '';
  filterName    = '';

  get loading(): boolean { return this.salaryLoading || this.othersLoading; }

  get displayedPayments(): SalaryPaymentResponse[] {
    return this.payments.filter(p =>
      (!this.filterStatus || p.status === this.filterStatus) &&
      (!this.filterName   || p.recipientName.toLowerCase().includes(this.filterName.toLowerCase()))
    );
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
    this.loadSalary();
    this.loadOthers();
  }

  loadSalary(): void {
    if (!this.selectedMonth) return;
    this.salaryLoading = true;
    this.salaryError   = false;

    let pending: SalaryPaymentResponse[] = [];
    let monthPayments: SalaryPaymentResponse[] = [];
    let loaded = 0;

    const done = (ok: boolean) => {
      if (!ok) this.salaryError = true;
      if (++loaded === 2) {
        const ids = new Set(monthPayments.map(x => x.id));
        this.pendingApprovals = pending;
        this.payments = [...monthPayments, ...pending.filter(x => !ids.has(x.id))];
        this.salaryLoading = false;
        this.cdr.detectChanges();
      }
    };

    this.salaryService.getPendingApprovalPayments().subscribe({
      next: p  => { pending = p; done(true); },
      error: () => done(true),
    });

    this.salaryService.getAllPayments(this.selectedMonth).subscribe({
      next: p  => { monthPayments = p; done(true); },
      error: () => done(false),
    });
  }

  loadOthers(): void {
    this.othersLoading = true;
    let loaded = 0;
    const done = () => {
      if (++loaded === 2) { this.othersLoading = false; this.cdr.detectChanges(); }
    };

    this.workerFinanceService.getAllTabPurchases().subscribe({
      next: list => { this.tabPurchases = list; done(); },
      error: ()  => { this.tabPurchases = []; done(); },
    });

    this.workerFinanceService.getAllAdvanceBonus().subscribe({
      next: list => { this.advanceBonuses = list; done(); },
      error: ()  => { this.advanceBonuses = []; done(); },
    });
  }

  approve(p: SalaryPaymentResponse): void {
    const overMsg = p.overSalary ? `\n\n⚠️ WARNING: This will exceed ${p.recipientName}'s monthly salary of Rs ${p.monthlySalary?.toLocaleString()}.` : '';
    if (!confirm(`Approve Rs ${p.amount.toLocaleString()} for ${p.recipientName} (${p.month})?${overMsg}`)) return;
    this.salaryService.approve(p.id).subscribe({
      next: () => this.loadSalary(),
      error: (err) => alert(err?.error?.message ?? 'Failed to approve.'),
    });
  }

  reject(p: SalaryPaymentResponse): void {
    const reason = prompt('Rejection reason (optional):') ?? '';
    this.salaryService.reject(p.id, reason).subscribe({
      next: () => this.loadSalary(),
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
