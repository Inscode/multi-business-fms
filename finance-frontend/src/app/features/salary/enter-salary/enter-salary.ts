import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { SalaryPaymentResponse, SalaryRecipientResponse, SalaryService } from '../../../core/services/salary';
import { WorkerAdvanceBonusResponse, WorkerFinanceService, WorkerTabPurchaseResponse } from '../../../core/services/worker-finance';

@Component({
  selector: 'app-enter-salary',
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
  ],
  templateUrl: './enter-salary.html',
  styleUrl: './enter-salary.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EnterSalary implements OnInit {
  recipients: SalaryRecipientResponse[] = [];
  myPayments: SalaryPaymentResponse[] = [];
  paidTabPurchases: WorkerTabPurchaseResponse[] = [];
  outstandingAdvances: WorkerAdvanceBonusResponse[] = [];

  recipientId: number | null = null;
  month = new Date().toISOString().substring(0, 7);
  amount = 0;
  paymentDate = new Date().toISOString().split('T')[0];
  notes = '';

  submitting = false;
  errorMsg = '';
  successMsg = '';

  get needsApproval(): boolean { return this.amount > 5000; }

  get totalTabDeductions(): number {
    return this.paidTabPurchases.reduce((s, t) => s + t.totalAmount, 0);
  }

  get totalAdvanceDeductions(): number {
    return this.outstandingAdvances.reduce((s, a) => s + (a.amount - a.recoveredAmount), 0);
  }

  get totalPendingDeductions(): number {
    return this.totalTabDeductions + this.totalAdvanceDeductions;
  }

  get suggestedNet(): number {
    const recipient = this.recipients.find(r => r.id === this.recipientId);
    const gross = recipient?.monthlySalary ?? 0;
    return Math.max(0, gross - this.totalPendingDeductions);
  }

  constructor(
    private salaryService: SalaryService,
    private workerFinanceService: WorkerFinanceService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.salaryService.getActiveRecipients().subscribe({
      next: (r) => { this.recipients = r; this.cdr.detectChanges(); },
      error: () => {},
    });
    this.loadMyPayments();
  }

  onWorkerOrMonthChange(): void {
    if (!this.recipientId || !this.month) return;
    this.paidTabPurchases = [];
    this.outstandingAdvances = [];

    this.workerFinanceService.getPaidTabPurchasesForMonth(this.recipientId, this.month).subscribe({
      next: (list) => { this.paidTabPurchases = list; this.cdr.detectChanges(); },
      error: () => { this.paidTabPurchases = []; },
    });

    this.workerFinanceService.getOutstandingAdvances(this.recipientId).subscribe({
      next: (list) => { this.outstandingAdvances = list; this.cdr.detectChanges(); },
      error: () => { this.outstandingAdvances = []; },
    });
  }

  loadMyPayments(): void {
    this.salaryService.getMyPayments().subscribe({
      next: (p) => { this.myPayments = p.slice(0, 10); this.cdr.detectChanges(); },
      error: () => {},
    });
  }

  submit(): void {
    if (!this.recipientId || !this.month || this.amount <= 0) return;
    this.submitting = true;
    this.errorMsg = '';
    this.successMsg = '';

    this.salaryService.createPayment({
      recipientId: this.recipientId,
      month: this.month,
      amount: this.amount,
      paymentDate: this.paymentDate,
      notes: this.notes || undefined,
    }).subscribe({
      next: (p) => {
        this.submitting = false;
        this.successMsg = p.status === 'PENDING_APPROVAL'
          ? 'Submitted — waiting for owner/admin approval (amount > Rs 5,000).'
          : 'Salary payment recorded successfully.';
        this.amount = 0;
        this.notes = '';
        this.paidTabPurchases = [];
        this.outstandingAdvances = [];
        this.loadMyPayments();
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.submitting = false;
        this.errorMsg = err?.error?.message ?? 'Failed to record payment.';
        this.cdr.detectChanges();
      },
    });
  }

  statusLabel(s: string): string {
    const map: Record<string, string> = {
      RECORDED: 'Recorded', PENDING_APPROVAL: 'Pending Approval', APPROVED: 'Approved', REJECTED: 'Rejected',
    };
    return map[s] ?? s;
  }
}
