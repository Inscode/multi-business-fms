import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { Auth } from '../../../core/services/auth';
import { type CashFundResponse, CashBalanceResponse, CashFundService } from '../../../core/services/cash-fund';
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
    MatDatepickerModule,
    MatNativeDateModule,
  ],
  templateUrl: './expense-list.html',
  styleUrl: './expense-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExpenseList implements OnInit {
  expenses: ExpenseResponse[] = [];
  pendingReviewExpenses: ExpenseResponse[] = [];
  loading = true;
  error = false;

  balance: CashBalanceResponse | null = null;
  showAddFunds = false;
  showFundHistory = false;
  fundHistory: CashFundResponse[] = [];
  fundHistoryLoading = false;
  newFundAmount = 0;
  newFundDate = (() => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`; })();
  newFundDescription = '';
  addingFund = false;

  filterBusiness = '';
  filterCategory = '';
  filterStatus = '';
  filterFrom: Date | null = null;
  filterTo: Date | null = null;

  businesses = ['RAINCO', 'RETAIL_SHOP', 'PLASTIC', 'HARDWARE', 'STATIONERY'];
  categories = ['FUEL', 'TEA', 'PARKING', 'REPAIR', 'PETTY_CASH', 'DRIVER_SALARY', 'OTHER'];

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
    const fmt = (d: Date) => d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
    if (this.filterFrom && this.filterTo) {
      const sameDay = this.filterFrom.toDateString() === this.filterTo.toDateString();
      return sameDay ? fmt(this.filterFrom) : `${fmt(this.filterFrom)} – ${fmt(this.filterTo)}`;
    }
    if (this.filterFrom) return `From ${fmt(this.filterFrom)}`;
    if (this.filterTo)   return `Until ${fmt(this.filterTo)}`;
    return 'All time';
  }

  private toIsoDate(d: Date | null): string | undefined {
    if (!d) return undefined;
    // Use LOCAL date parts — toISOString() converts to UTC and shifts the date in non-UTC timezones
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
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

  toggleFundHistory(): void {
    this.showFundHistory = !this.showFundHistory;
    if (this.showFundHistory && this.fundHistory.length === 0) {
      this.loadFundHistory();
    }
  }

  loadFundHistory(): void {
    this.fundHistoryLoading = true;
    this.cdr.detectChanges();
    this.cashFundService.getHistory().subscribe({
      next: (h) => { this.fundHistory = h; this.fundHistoryLoading = false; this.cdr.detectChanges(); },
      error: () => { this.fundHistoryLoading = false; this.cdr.detectChanges(); },
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
        if (this.showFundHistory) this.loadFundHistory();
        this.cdr.detectChanges();
      },
      error: () => { this.addingFund = false; this.cdr.detectChanges(); },
    });
  }

  setToday(): void {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    this.filterFrom = today;
    this.filterTo   = new Date(today);
    this.load();
  }

  clearDateFilters(): void {
    this.filterFrom = null;
    this.filterTo   = null;
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = false;

    // Always load pending review items regardless of date filter
    if (this.isOwnerOrAdmin) {
      this.expenseService.getAll({ status: 'PENDING_REVIEW' }).subscribe({
        next: (e) => { this.pendingReviewExpenses = e; this.cdr.detectChanges(); },
        error: () => { this.pendingReviewExpenses = []; },
      });
    }

    this.expenseService.getAll({
      business: this.filterBusiness || undefined,
      category: this.filterCategory || undefined,
      status:   this.filterStatus   || undefined,
      from:     this.toIsoDate(this.filterFrom),
      to:       this.toIsoDate(this.filterTo),
    }).subscribe({
      next: (e) => { this.expenses = e; this.loading = false; this.cdr.detectChanges(); },
      error: () => { this.error = true; this.loading = false; this.cdr.detectChanges(); },
    });
  }

  review(expense: ExpenseResponse): void {
    if (!confirm(`Mark expense of Rs ${expense.amount} (${expense.category}) as reviewed?`)) return;
    this.expenseService.review(expense.id).subscribe({
      next: (updated) => {
        // Remove from pending banner
        this.pendingReviewExpenses = this.pendingReviewExpenses.filter(e => e.id !== expense.id);
        // Update in-place if already visible, otherwise insert at top so it's not lost due to date filter
        const idx = this.expenses.findIndex(e => e.id === expense.id);
        if (idx >= 0) {
          const copy = [...this.expenses];
          copy[idx] = updated;
          this.expenses = copy;
        } else {
          this.expenses = [updated, ...this.expenses];
        }
        this.cdr.detectChanges();
      },
      error: () => alert('Failed to mark as reviewed.'),
    });
  }

  categoryLabel(cat: string): string {
    const map: Record<string, string> = {
      FUEL: 'Fuel', TEA: 'Tea', PARKING: 'Parking',
      REPAIR: 'Repair', PETTY_CASH: 'Petty Cash',
      DRIVER_SALARY: 'Driver Salary', OTHER: 'Other',
    };
    return map[cat] ?? cat;
  }
}