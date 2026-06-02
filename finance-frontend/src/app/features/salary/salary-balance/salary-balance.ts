import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { SalaryPaymentResponse, SalaryRecipientResponse, SalaryService } from '../../../core/services/salary';
import { WorkerAdvanceBonusResponse, WorkerFinanceService, WorkerTabPurchaseResponse } from '../../../core/services/worker-finance';

interface EmployeeBalance {
  recipientId: number;
  name: string;
  designation: string;
  monthlySalary: number;
  totalPaid: number;
  pendingApproval: number;
  tabDeductions: number;
  outstandingAdvance: number;
  salaryRemaining: number;
}

@Component({
  selector: 'app-salary-balance',
  standalone: true,
  imports: [
    CommonModule, FormsModule, DecimalPipe,
    MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './salary-balance.html',
  styleUrl: './salary-balance.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SalaryBalance implements OnInit {
  month = new Date().toISOString().substring(0, 7);
  balances: EmployeeBalance[] = [];
  loading = false;

  constructor(
    private salaryService: SalaryService,
    private workerFinanceService: WorkerFinanceService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    if (!this.month) return;
    this.loading = true;
    this.cdr.detectChanges();

    const [year, mon] = this.month.split('-').map(Number);
    const fromDate = `${this.month}-01`;
    const lastDay = new Date(year, mon, 0).getDate();
    const toDate = `${this.month}-${String(lastDay).padStart(2, '0')}`;

    let recipients: SalaryRecipientResponse[] = [];
    let payments: SalaryPaymentResponse[] = [];
    let tabPurchases: WorkerTabPurchaseResponse[] = [];
    let advances: WorkerAdvanceBonusResponse[] = [];
    let loaded = 0;
    const total = 4;

    const done = () => {
      if (++loaded < total) return;
      this.balances = this.buildBalances(recipients, payments, tabPurchases, advances);
      this.loading = false;
      this.cdr.detectChanges();
    };

    this.salaryService.getAllRecipients().subscribe({
      next: (r) => { recipients = r.filter(x => x.active); done(); },
      error: () => done(),
    });

    this.salaryService.getAllPayments(this.month).subscribe({
      next: (p) => { payments = p; done(); },
      error: () => done(),
    });

    this.workerFinanceService.getTabPurchasesByDateRange(fromDate, toDate).subscribe({
      next: (t) => { tabPurchases = t; done(); },
      error: () => done(),
    });

    this.workerFinanceService.getAllAdvanceBonus().subscribe({
      next: (a) => { advances = a.filter(x => x.status === 'PAID' && !x.fullyRecovered); done(); },
      error: () => done(),
    });
  }

  private buildBalances(
    recipients: SalaryRecipientResponse[],
    payments: SalaryPaymentResponse[],
    tabPurchases: WorkerTabPurchaseResponse[],
    advances: WorkerAdvanceBonusResponse[],
  ): EmployeeBalance[] {
    return recipients.map(r => {
      const rPayments = payments.filter(p => p.recipientId === r.id);
      const totalPaid = rPayments
        .filter(p => p.status === 'RECORDED' || p.status === 'APPROVED')
        .reduce((s, p) => s + p.amount, 0);
      const pendingApproval = rPayments
        .filter(p => p.status === 'PENDING_APPROVAL')
        .reduce((s, p) => s + p.amount, 0);
      const tabDeductions = tabPurchases
        .filter(t => t.recipientId === r.id)
        .reduce((s, t) => s + t.totalAmount, 0);
      const outstandingAdvance = advances
        .filter(a => a.recipientId === r.id)
        .reduce((s, a) => s + (a.amount - a.recoveredAmount), 0);
      const gross = r.monthlySalary ?? 0;
      const salaryRemaining = gross - totalPaid - tabDeductions - outstandingAdvance;

      return {
        recipientId: r.id,
        name: r.name,
        designation: r.designation,
        monthlySalary: gross,
        totalPaid,
        pendingApproval,
        tabDeductions,
        outstandingAdvance,
        salaryRemaining,
      };
    });
  }

  get grandGross(): number      { return this.balances.reduce((s, b) => s + b.monthlySalary, 0); }
  get grandPaid(): number       { return this.balances.reduce((s, b) => s + b.totalPaid, 0); }
  get grandPending(): number    { return this.balances.reduce((s, b) => s + b.pendingApproval, 0); }
  get grandTab(): number        { return this.balances.reduce((s, b) => s + b.tabDeductions, 0); }
  get grandAdvance(): number    { return this.balances.reduce((s, b) => s + b.outstandingAdvance, 0); }
  get grandRemaining(): number  { return this.balances.reduce((s, b) => s + b.salaryRemaining, 0); }

  designationLabel(d: string): string {
    const map: Record<string, string> = {
      WORKER: 'Worker', ACCOUNTANT: 'Accountant', MAIN_ACCOUNTANT: 'Main Accountant',
      DRIVER: 'Driver', OTHER: 'Other',
    };
    return map[d] ?? d;
  }
}
