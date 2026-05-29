import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { SalaryPaymentResponse, SalaryService } from '../../../core/services/salary';

@Component({
  selector: 'app-salary-review',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    DecimalPipe,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './salary-review.html',
  styleUrl: './salary-review.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SalaryReview implements OnInit {
  payments: SalaryPaymentResponse[] = [];
  loading = true;
  error = false;

  filterMonth = new Date().toISOString().substring(0, 7);
  filterStatus = '';

  get displayed(): SalaryPaymentResponse[] {
    return this.payments.filter(p =>
      !this.filterStatus || p.status === this.filterStatus
    );
  }

  get pendingCount(): number {
    return this.payments.filter(p => p.status === 'PENDING_APPROVAL').length;
  }

  get totalApproved(): number {
    return this.payments
      .filter(p => p.status === 'RECORDED' || p.status === 'APPROVED')
      .reduce((s, p) => s + p.amount, 0);
  }

  constructor(
    private salaryService: SalaryService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.error = false;
    this.salaryService.getAllPayments(this.filterMonth || undefined).subscribe({
      next: (p) => { this.payments = p; this.loading = false; this.cdr.detectChanges(); },
      error: () => { this.error = true; this.loading = false; this.cdr.detectChanges(); },
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
    const map: Record<string,string> = { RECORDED: 'Recorded', PENDING_APPROVAL: 'Pending', APPROVED: 'Approved', REJECTED: 'Rejected' };
    return map[s] ?? s;
  }
}