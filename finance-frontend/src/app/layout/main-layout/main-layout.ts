import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit, AfterViewInit, OnDestroy, HostListener, ViewChild } from '@angular/core';
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
export class MainLayoutComponent implements OnInit, AfterViewInit, OnDestroy {
  @ViewChild('sidenav') sidenav!: MatSidenav;
  collapsed = false;
  isMobile  = false;
  navHidden = false;
  private lastScrollTop = 0;
  private resizeTimer: ReturnType<typeof setTimeout> | null = null;
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
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.checkScreen();
    if (this.showUsers) this.loadPendingCount();
    if (this.isAdminOrOwner) this.loadPendingReturns();
    if (this.isAdminOrOwner) this.loadPendingExpenses();
    if (this.isAdminOrOwner) this.loadPendingSalary();
    if (this.showWorkerFinanceReview) this.loadWorkerFinanceCounts();
  }

  ngAfterViewInit(): void {}

  ngOnDestroy(): void {
    if (this.resizeTimer) clearTimeout(this.resizeTimer);
  }

  @HostListener('window:resize')
  onResize(): void {
    if (this.resizeTimer) clearTimeout(this.resizeTimer);
    this.resizeTimer = setTimeout(() => this.checkScreen(), 150);
  }

  checkScreen(): void {
    this.isMobile = window.innerWidth < 768;
    this.cdr.markForCheck();
  }

  onPageScroll(event: Event): void {
    if (!this.isMobile) return;
    const el = event.target as HTMLElement;
    const st = el.scrollTop;
    if (Math.abs(st - this.lastScrollTop) < 8) return;
    this.navHidden = st > this.lastScrollTop && st > 60;
    this.lastScrollTop = st;
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
      next: (list) => setTimeout(() => { this.pendingEditRequests = list.length; this.cdr.markForCheck(); }),
      error: () => {},
    });
  }

  private loadPendingReturns(): void {
    this.billReturnService.getPendingCount().subscribe({
      next: (res) => setTimeout(() => { this.pendingReturns = res.count; this.cdr.markForCheck(); }),
      error: () => {},
    });
  }

  private loadPendingExpenses(): void {
    this.expenseService.getPendingCount().subscribe({
      next: (res) => setTimeout(() => { this.pendingExpenses = res.count; this.cdr.markForCheck(); }),
      error: () => {},
    });
  }

  private loadPendingSalary(): void {
    this.salaryService.getPendingCount().subscribe({
      next: (res) => setTimeout(() => { this.pendingSalary = res.count; this.cdr.markForCheck(); }),
      error: () => {},
    });
  }

  private loadWorkerFinanceCounts(): void {
    this.workerFinanceService.getTabCounts().subscribe({
      next: (res) => setTimeout(() => {
        this.pendingWorkerFinanceOwner += res.pendingOwner;
        this.pendingWorkerFinanceAdmin += res.pendingAdmin;
        this.cdr.markForCheck();
      }),
      error: () => {},
    });
    this.workerFinanceService.getAdvanceBonusCounts().subscribe({
      next: (res) => setTimeout(() => {
        this.pendingWorkerFinanceOwner += res.pendingOwner;
        this.pendingWorkerFinanceAdmin += res.pendingAdmin;
        this.cdr.markForCheck();
      }),
      error: () => {},
    });
  }

  get currentRole(): string         { return this.auth.getRole() ?? ''; }
  get isDemo(): boolean             { return this.auth.isDemo; }
  get isAdminOrOwner(): boolean     { return ['ADMIN', 'OWNER'].includes(this.currentRole); }
  get isShopAccountant(): boolean   { return this.currentRole === 'SHOP_ACCOUNTANT'; }
  get showBills(): boolean          { return !this.isShopAccountant; }
  get showInvoicing(): boolean      { return ['ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT'].includes(this.currentRole); }
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
  get showAgingReport(): boolean         { return ['ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT'].includes(this.currentRole); }
  get showCollectionHealth(): boolean    { return this.currentRole === 'ADMIN'; }
  get showCashFlow(): boolean            { return ['ADMIN', 'OWNER', 'MAIN_ACCOUNTANT'].includes(this.currentRole); }
  get showCustomers(): boolean           { return ['ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT'].includes(this.currentRole); }
  get showWorkerCollections(): boolean   { return ['ADMIN', 'OWNER', 'ACCOUNTANT', 'MAIN_ACCOUNTANT'].includes(this.currentRole); }
  get showTasks(): boolean               { return ['ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT', 'DELIVERY'].includes(this.currentRole); }
  get showBackorders(): boolean          { return ['ADMIN', 'OWNER', 'ACCOUNTANT', 'MAIN_ACCOUNTANT'].includes(this.currentRole); }
  get showTimeLog(): boolean             { return this.currentRole === 'ADMIN'; }
}