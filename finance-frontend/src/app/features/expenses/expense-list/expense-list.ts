import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { Auth } from '../../../core/services/auth';
import { CashBalanceResponse, CashFundService } from '../../../core/services/cash-fund';
import { ExpenseResponse, ExpenseService } from '../../../core/services/expense';

@Component({
  selector: 'app-expense-list',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    DatePipe,
    DecimalPipe,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './expense-list.html',
  styleUrl: './expense-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExpenseList implements OnInit {
  expenses: ExpenseResponse[] = [];
  loading = true;
  error = false;

  balance: CashBalanceResponse | null = null;
  showAddFunds = false;
  newFundAmount = 0;
  newFundDate = new Date().toISOString().split('T')[0];
  newFundDescription = '';
  addingFund = false;

  filterBusiness = '';
  filterCategory = '';
  filterStatus = '';
  filterDate = '';
  filterMonth = '';

  businesses = ['RAINCO', 'RETAIL_SHOP', 'PLASTIC', 'HARDWARE', 'STATIONERY'];
  categories = ['FUEL', 'TEA', 'PARKING', 'REPAIR', 'SALARY', 'PETTY_CASH', 'OTHER'];

  get isOwnerOrAdmin(): boolean {
    const r = this.auth.getRole();
    return r === 'ADMIN' || r === 'OWNER';
  }

  get balanceClass(): string {
    if (!this.balance) return '';
    return this.balance.balance < 0 ? 'negative' : this.balance.balance < 2000 ? 'low' : 'healthy';
  }

  get total(): number {
    return this.expenses.reduce((s, e) => s + e.amount, 0);
  }

  get pendingCount(): number {
    return this.expenses.filter(e => e.status === 'PENDING_REVIEW').length;
  }

  get activeFilterLabel(): string {
    if (this.filterDate) return new Date(this.filterDate + 'T00:00:00').toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
    if (this.filterMonth) {
      const [y, m] = this.filterMonth.split('-');
      return new Date(+y, +m - 1).toLocaleDateString('en-GB', { month: 'long', year: 'numeric' });
    }
    return 'All time';
  }

  constructor(
    private expenseService: ExpenseService,
    private cashFundService: CashFundService,
    private auth: Auth,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.setToday();
    this.loadBalance();
  }

  loadBalance(): void {
    this.cashFundService.getBalance().subscribe({
      next: (b) => { this.balance = b; this.cdr.detectChanges(); },
      error: () => {},
    });
  }

  saveAddFunds(): void {
    if (!this.newFundAmount || this.newFundAmount <= 0) return;
    this.addingFund = true;
    this.cashFundService.addFund({
      amount: this.newFundAmount,
      date: this.newFundDate,
      description: this.newFundDescription || undefined,
    }).subscribe({
      next: () => {
        this.addingFund = false;
        this.showAddFunds = false;
        this.newFundAmount = 0;
        this.newFundDescription = '';
        this.loadBalance();
        this.cdr.detectChanges();
      },
      error: () => {
        this.addingFund = false;
        this.cdr.detectChanges();
      },
    });
  }

  setToday(): void {
    this.filterDate = new Date().toISOString().split('T')[0];
    this.filterMonth = '';
    this.load();
  }

  onDateChange(): void {
    this.filterMonth = '';
    this.load();
  }

  onMonthChange(): void {
    this.filterDate = '';
    this.load();
  }

  clearDateFilters(): void {
    this.filterDate = '';
    this.filterMonth = '';
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = false;

    let from: string | undefined;
    let to: string | undefined;

    if (this.filterDate) {
      from = this.filterDate;
      to   = this.filterDate;
    } else if (this.filterMonth) {
      const [y, m] = this.filterMonth.split('-').map(Number);
      from = `${this.filterMonth}-01`;
      to   = new Date(y, m, 0).toISOString().split('T')[0];
    }

    this.expenseService.getAll({
      business: this.filterBusiness || undefined,
      category: this.filterCategory || undefined,
      status:   this.filterStatus   || undefined,
      from,
      to,
    }).subscribe({
      next: (e) => {
        this.expenses = e;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.error = true;
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  review(expense: ExpenseResponse): void {
    if (!confirm(`Mark expense of Rs ${expense.amount} (${expense.category}) as reviewed?`)) return;
    this.expenseService.review(expense.id).subscribe({
      next: () => this.load(),
      error: () => alert('Failed to mark as reviewed.'),
    });
  }

  categoryLabel(cat: string): string {
    const map: Record<string, string> = {
      FUEL: 'Fuel', TEA: 'Tea', PARKING: 'Parking',
      REPAIR: 'Repair', SALARY: 'Salary', PETTY_CASH: 'Petty Cash', OTHER: 'Other',
    };
    return map[cat] ?? cat;
  }
}