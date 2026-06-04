import { CommonModule } from '@angular/common';
import { Component, OnInit, AfterViewInit, HostListener, ViewChild } from '@angular/core';
import { MatRippleModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NavbarComponent } from '../navbar/navbar';
import { Auth } from '../../core/services/auth';
import { EditRequestService } from '../../core/services/edit-request';
import { BillReturnService } from '../../core/services/bill-return';
import { ExpenseService } from '../../core/services/expense';
import { SalaryService } from '../../core/services/salary';
import { WorkerFinanceService } from '../../core/services/worker-finance';

@Component({
  selector: 'app-main-layout',
  templateUrl: './main-layout.html',
  styleUrls: ['./main-layout.scss'],
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatIconModule,
    MatRippleModule,
    NavbarComponent,
  ]
})
export class MainLayoutComponent implements OnInit, AfterViewInit {
  @ViewChild('sidenav') sidenav!: MatSidenav;
  collapsed = false;
  isMobile  = false;
  pendingEditRequests = 0;
  pendingReturns = 0;
  pendingExpenses = 0;
  pendingSalary = 0;
  pendingWorkerFinanceOwner = 0;
  pendingWorkerFinanceAdmin = 0;

  constructor(
    private auth: Auth,
    private editRequestService: EditRequestService,
    private billReturnService: BillReturnService,
    private expenseService: ExpenseService,
    private salaryService: SalaryService,
    private workerFinanceService: WorkerFinanceService,
  ) {}

  ngOnInit(): void {
    this.checkScreen();
    if (this.showUsers) this.loadPendingCount();
    if (this.showReturns) this.loadPendingReturns();
    if (this.showExpenses) this.loadPendingExpenses();
    if (this.showSalary) this.loadPendingSalary();
    if (this.showWorkerFinanceReview) this.loadWorkerFinanceCounts();
  }

  ngAfterViewInit(): void {}

  @HostListener('window:resize')
  checkScreen(): void {
    this.isMobile = window.innerWidth < 768;
  }

  toggleCollapse(): void {
    if (this.isMobile) {
      this.sidenav.toggle();
    } else {
      this.collapsed = !this.collapsed;
    }
  }

  private loadPendingCount(): void {
    this.editRequestService.getPending().subscribe({
      next: (list) => this.pendingEditRequests = list.length,
      error: () => this.pendingEditRequests = 0,
    });
  }

  private loadPendingReturns(): void {
    this.billReturnService.getPendingCount().subscribe({
      next: (res) => this.pendingReturns = res.count,
      error: () => this.pendingReturns = 0,
    });
  }

  private loadPendingExpenses(): void {
    this.expenseService.getPendingCount().subscribe({
      next: (res) => this.pendingExpenses = res.count,
      error: () => this.pendingExpenses = 0,
    });
  }

  private loadPendingSalary(): void {
    this.salaryService.getPendingCount().subscribe({
      next: (res) => this.pendingSalary = res.count,
      error: () => this.pendingSalary = 0,
    });
  }

  private loadWorkerFinanceCounts(): void {
    this.workerFinanceService.getTabCounts().subscribe({
      next: (res) => {
        this.pendingWorkerFinanceOwner += res.pendingOwner;
        this.pendingWorkerFinanceAdmin += res.pendingAdmin;
      },
      error: () => {},
    });
    this.workerFinanceService.getAdvanceBonusCounts().subscribe({
      next: (res) => {
        this.pendingWorkerFinanceOwner += res.pendingOwner;
        this.pendingWorkerFinanceAdmin += res.pendingAdmin;
      },
      error: () => {},
    });
  }

  get currentRole(): string         { return this.auth.getRole() ?? ''; }
  get isShopAccountant(): boolean   { return this.currentRole === 'SHOP_ACCOUNTANT'; }
  get showBills(): boolean          { return !this.isShopAccountant; }
  get showPayments(): boolean       { return !this.isShopAccountant; }
  get showStaff(): boolean          { return ['ADMIN', 'MAIN_ACCOUNTANT'].includes(this.currentRole); }
  get showUsers(): boolean          { return this.currentRole === 'ADMIN'; }
  get showCollect(): boolean        { return this.currentRole === 'OWNER'; }
  get showEditRequests(): boolean   { return this.currentRole === 'ADMIN'; }
  get showReturns(): boolean         { return ['ADMIN', 'OWNER', 'ACCOUNTANT', 'MAIN_ACCOUNTANT'].includes(this.currentRole); }
  get showExpenses(): boolean       { return ['ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT'].includes(this.currentRole); }
  get showSalary(): boolean          { return ['ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT'].includes(this.currentRole); }
  get showStock(): boolean              { return ['ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT', 'OWNER'].includes(this.currentRole); }
  get showBillChecklist(): boolean       { return ['ADMIN', 'OWNER'].includes(this.currentRole); }
  get showWorkerFinance(): boolean       { return ['ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT'].includes(this.currentRole); }
  get showWorkerFinanceReview(): boolean { return ['ADMIN', 'OWNER'].includes(this.currentRole); }
  get showCustomers(): boolean           { return ['ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT'].includes(this.currentRole); }
}