import { Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTabsModule } from '@angular/material/tabs';
import { SalaryRecipientResponse, SalaryService } from '../../../core/services/salary';
import { WorkerAdvanceBonusResponse, WorkerFinanceService } from '../../../core/services/worker-finance';

@Component({
  selector: 'app-advance-bonus-entry',
  standalone: true,
  imports: [
    CommonModule, FormsModule, DecimalPipe,
    MatButtonModule, MatCardModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatIconModule, MatProgressSpinnerModule,
    MatTabsModule,
  ],
  templateUrl: './advance-bonus-entry.html',
  styleUrl: './advance-bonus-entry.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdvanceBonusEntry implements OnInit {
  recipients: SalaryRecipientResponse[] = [];
  myEntries: WorkerAdvanceBonusResponse[] = [];

  // Entry form
  recipientId: number | null = null;
  type: 'ADVANCE' | 'BONUS' = 'ADVANCE';
  amount: number | null = null;
  reason = '';
  month = new Date().toISOString().substring(0, 7);
  notes = '';
  submitting = false;
  errorMsg = '';
  successMsg = '';

  // Recovery section
  recoveryRecipientId: number | null = null;
  recoveryMonth = new Date().toISOString().substring(0, 7);
  outstandingAdvances: WorkerAdvanceBonusResponse[] = [];
  recoveryAmounts: Record<number, number> = {};
  recovering: Record<number, boolean> = {};
  recoveryMsg = '';
  recoveryError = '';

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
    this.loadMyEntries();
  }

  loadMyEntries(): void {
    this.workerFinanceService.getAllAdvanceBonus().subscribe({
      next: (list) => { this.myEntries = list.slice(0, 20); this.cdr.detectChanges(); },
      error: () => {},
    });
  }

  submit(): void {
    if (!this.recipientId || !this.amount || !this.reason || !this.month) return;
    this.submitting = true;
    this.errorMsg = '';
    this.successMsg = '';

    this.workerFinanceService.createAdvanceBonus({
      recipientId: this.recipientId,
      type: this.type,
      amount: this.amount,
      reason: this.reason,
      month: this.month,
      notes: this.notes || undefined,
    }).subscribe({
      next: () => {
        this.submitting = false;
        this.successMsg = `${this.type === 'ADVANCE' ? 'Advance' : 'Bonus'} submitted — awaiting owner approval.`;
        this.amount = null;
        this.reason = '';
        this.notes = '';
        this.loadMyEntries();
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.submitting = false;
        this.errorMsg = err?.error?.message ?? 'Failed to submit.';
        this.cdr.detectChanges();
      },
    });
  }

  loadOutstandingAdvances(): void {
    if (!this.recoveryRecipientId) return;
    this.outstandingAdvances = [];
    this.recoveryAmounts = {};
    this.workerFinanceService.getOutstandingAdvances(this.recoveryRecipientId).subscribe({
      next: (list) => {
        this.outstandingAdvances = list;
        list.forEach(a => { this.recoveryAmounts[a.id] = 0; });
        this.cdr.detectChanges();
      },
      error: () => { this.cdr.detectChanges(); },
    });
  }

  outstanding(a: WorkerAdvanceBonusResponse): number {
    return a.amount - a.recoveredAmount;
  }

  recoverAdvance(a: WorkerAdvanceBonusResponse): void {
    const amt = this.recoveryAmounts[a.id];
    if (!amt || amt <= 0) return;
    if (amt > this.outstanding(a)) {
      this.recoveryError = `Recovery amount exceeds outstanding balance of Rs ${this.outstanding(a).toLocaleString()}`;
      this.cdr.detectChanges();
      return;
    }
    this.recovering[a.id] = true;
    this.recoveryError = '';
    this.recoveryMsg = '';

    this.workerFinanceService.recoverAdvance(a.id, amt, this.recoveryMonth).subscribe({
      next: () => {
        this.recovering[a.id] = false;
        this.recoveryMsg = `Recovered Rs ${amt.toLocaleString()} from advance for ${a.recipientName}.`;
        this.recoveryAmounts[a.id] = 0;
        this.loadOutstandingAdvances();
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.recovering[a.id] = false;
        this.recoveryError = err?.error?.message ?? 'Recovery failed.';
        this.cdr.detectChanges();
      },
    });
  }

  statusLabel(s: string): string {
    const map: Record<string, string> = {
      PENDING_OWNER: 'Pending Owner', OWNER_APPROVED: 'Owner Approved', PAID: 'Paid', REJECTED: 'Rejected',
    };
    return map[s] ?? s;
  }
}
