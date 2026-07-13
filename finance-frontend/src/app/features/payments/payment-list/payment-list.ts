import { CommonModule, DatePipe, DecimalPipe, LowerCasePipe } from '@angular/common';
import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { localDateStr } from '../../../core/utils/date-utils';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Payment, PaymentResponse } from '../../../core/services/payment';
import { Auth } from '../../../core/services/auth';
import { Router, RouterLink } from '@angular/router';
import { ReturnChequeDialog } from '../return-cheque-dialog/return-cheque-dialog';
import { ConfirmDialog } from '../../../shared/confirm-dialog/confirm-dialog';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap, catchError } from 'rxjs/operators';
import { of } from 'rxjs';

@Component({
  selector: 'app-payment-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatFormFieldModule,
    MatMenuModule,
    MatProgressSpinnerModule,
    MatPaginatorModule,
    MatDialogModule,
    MatTooltipModule,
    DecimalPipe,
    LowerCasePipe,
    DatePipe,
    RouterLink,
    MatInputModule,
    MatDatepickerModule,
    MatNativeDateModule,
  ],
  templateUrl: './payment-list.html',
  styleUrl: './payment-list.scss',
})

export class PaymentList implements OnInit, AfterViewInit, OnDestroy {
  dataSource = new MatTableDataSource<PaymentResponse>([]);
  loading = true;
  error = false;

  searchQuery = '';
  selectedArea = '';
  selectedStatus = 'ENTERED';   // default: pending only
  showAll = false;

  filterFrom = '';
  filterTo   = '';

  private allPayments: PaymentResponse[] = [];

  // ── Cheque number search ───────────────────────────────────────
  chequeNumberQuery = '';
  chequeSearchResults: PaymentResponse[] = [];
  chequeSearchLoading = false;
  chequeSearchActive = false;
  private chequeSearch$ = new Subject<string>();

  // ── Future cheques panel ───────────────────────────────────────
  futureChequesPanelOpen = false;
  private allFutureCheques: PaymentResponse[] = [];
  futureCheques: PaymentResponse[] = [];
  futureChequeLoading = false;
  futureChequeRefreshing = false;
  futureCustomerQuery = '';

  @ViewChild(MatPaginator) paginator!: MatPaginator;

  areas = [
    'Ambagasdowa', 'Badalkumbura', 'Badulla', 'Bandarawela', 'Beragala',
    'Bogakumbura', 'Boralanda', 'Demodara', 'Diyatalawa', 'Ella',
    'Etampitiya', 'Haldummulla', 'Hali-Ela', 'Hasalaka', 'Haputale',
    'Hopton', 'Kandaketiya', 'Keppatipola', 'Kumbalwela', 'Lunugala',
    'Lunuwatta', 'Mahiyanganaya', 'Meegahakivula', 'Passara',
    'Uva-Paranagama', 'Welimada',
  ];

  
  statuses = ['', 'ENTERED', 'CONFIRMED', 'REJECTED', 'RETURNED'];

  displayedColumns = ['billNumber', 'customerName', 'amount',
                      'type', 'enteredBy', 'date', 'status', 'actions'];

  get isAdmin(): boolean { return this.auth.getRole() === 'ADMIN'; }
  get isAccountant(): boolean { return this.auth.getRole() === 'ACCOUNTANT'; }
  get isOwner(): boolean { return this.auth.getRole() === 'OWNER'; }
  get canSeeChequeTools(): boolean { return this.isAdmin || this.isOwner; }

  constructor(
    private paymentService: Payment,
    private auth: Auth,
    private router: Router,
    private cdr: ChangeDetectorRef,
    private dialog: MatDialog
  ) {
    this.filterFrom = this.daysAgo(30);
    this.filterTo   = this.today;
  }

  get today(): string { return localDateStr(); }

  daysAgo(n: number): string {
    const d = new Date();
    d.setDate(d.getDate() - n);
    return localDateStr(d);
  }

  get filterFromDate(): Date | null { return this.filterFrom ? new Date(this.filterFrom + 'T00:00:00') : null; }
  get filterToDate():   Date | null { return this.filterTo   ? new Date(this.filterTo   + 'T00:00:00') : null; }
  get todayDate(): Date { return new Date(); }

  onFromDateChange(e: { value: Date | null }): void {
    this.filterFrom = e.value ? this.formatDate(e.value) : '';
    this.load();
  }

  onToDateChange(e: { value: Date | null }): void {
    this.filterTo = e.value ? this.formatDate(e.value) : '';
    this.load();
  }

  private formatDate(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  ngOnInit(): void {
    this.load();

    this.chequeSearch$.pipe(
      debounceTime(350),
      distinctUntilChanged(),
      switchMap(q => {
        if (!q || q.length < 2) {
          this.chequeSearchActive = false;
          this.chequeSearchLoading = false;
          this.chequeSearchResults = [];
          this.cdr.detectChanges();
          return of(null);
        }
        this.chequeSearchLoading = true;
        this.chequeSearchActive = true;
        this.cdr.detectChanges();
        return this.paymentService.searchByChequeNumber(q).pipe(
          catchError(() => of(null))
        );
      }),
    ).subscribe(results => {
      if (results === null) { this.chequeSearchLoading = false; this.cdr.detectChanges(); return; }
      this.chequeSearchResults = results as PaymentResponse[];
      this.chequeSearchLoading = false;
      this.cdr.detectChanges();
    });
  }

  ngOnDestroy(): void {
    this.chequeSearch$.complete();
  }

  ngAfterViewInit(): void {
    this.dataSource.paginator = this.paginator;
  }

  toggleShowAll(): void {
    this.showAll = !this.showAll;
    this.selectedStatus = this.showAll ? '' : 'ENTERED';
    this.load();
  }

  onDateChange(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.error = false;
    this.cdr.detectChanges();

    this.paymentService.getAllPayments(
      this.selectedStatus || undefined,
      this.filterFrom || undefined,
      this.filterTo   || undefined,
    ).subscribe({
      next: (p) => {
        this.allPayments = p;
        this.loading = false;
        this.applyFilters();
        this.cdr.detectChanges();
      },
      error: () => {
        this.error   = true;
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  applyFilters(): void {
    const query = this.searchQuery.toLowerCase().trim();
    const filtered = this.allPayments.filter(p => {
      const matchesSearch = !query ||
        p.customerName.toLowerCase().includes(query) ||
        (p.billNumber ?? '').toLowerCase().includes(query);
      const matchesArea = !this.selectedArea || p.area === this.selectedArea;
      return matchesSearch && matchesArea;
    });
    this.dataSource.data = filtered;
    if (this.paginator) this.paginator.firstPage();
    this.cdr.detectChanges();
  }

  onSearchChange(): void { this.applyFilters(); }
  onAreaChange(): void   { this.applyFilters(); }
  onFilterChange(): void { this.load(); }

  editPayment(payment: PaymentResponse): void {
    this.router.navigate(['/payments/enter'], { state: { payment } });
  }

  confirmPayment(payment: PaymentResponse): void {
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Confirm Payment',
        message: `Confirm payment of Rs ${payment.paymentAmount} for ${payment.billNumber} — ${payment.customerName}?`,
        confirmText: 'Confirm',
        confirmColor: 'primary',
      },
      maxWidth: '95vw',
    }).afterClosed().subscribe(result => {
      if (!result?.confirmed) return;
      this.paymentService.confirmPayment(payment.id).subscribe({
        next: () => this.load(),
        error: () => this.load(),
      });
    });
  }

  rejectPayment(payment: PaymentResponse): void {
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Reject Payment',
        message: `Reject payment of Rs ${payment.paymentAmount} for ${payment.billNumber} — ${payment.customerName}?`,
        confirmText: 'Reject',
        confirmColor: 'warn',
        showInput: true,
        inputLabel: 'Reason for rejection (optional)',
      },
      maxWidth: '95vw',
    }).afterClosed().subscribe(result => {
      if (!result?.confirmed) return;
      this.paymentService.rejectPayment(payment.id, result.inputValue ?? '').subscribe({
        next: () => this.load(),
        error: (err) => this.load(),
      });
    });
  }

  canConfirm(payment: PaymentResponse): boolean {
    return (this.isAdmin || this.isOwner) && payment.status === 'ENTERED';
  }

  canEdit(payment: PaymentResponse): boolean {
    return !this.isOwner && payment.status === 'ENTERED' && (this.isAccountant || this.isAdmin);
  }

  canReturn(payment: PaymentResponse): boolean {
    return payment.status === 'CONFIRMED' &&
           payment.paymentType === 'CHEQUE' &&
           this.isAdmin;
  }

  canReject(payment: PaymentResponse): boolean {
    return this.isAdmin && payment.status === 'ENTERED';
  }

  canDelete(payment: PaymentResponse): boolean {
    return this.isAdmin && payment.status === 'ENTERED';
  }

  hasActions(payment: PaymentResponse): boolean {
    return this.canConfirm(payment) || this.canEdit(payment) || this.canReturn(payment) || this.canDelete(payment) || this.canReject(payment);
  }

  get futureChequeTotal(): number {
    return this.futureCheques.reduce((sum, p) => sum + p.paymentAmount, 0);
  }

  daysUntil(dateStr: string): number {
    const today = new Date(); today.setHours(0, 0, 0, 0);
    const target = new Date(dateStr + 'T00:00:00');
    return Math.round((target.getTime() - today.getTime()) / 86400000);
  }

  // ── Cheque number search ─────────────────────────────────────────────────
  onChequeNumberChange(): void {
    this.chequeSearch$.next(this.chequeNumberQuery.trim());
  }

  clearChequeSearch(): void {
    this.chequeNumberQuery = '';
    this.chequeSearchActive = false;
    this.chequeSearchResults = [];
    this.chequeSearch$.next('');
  }

  // ── Future cheques panel ─────────────────────────────────────────────────
  toggleFutureCheques(): void {
    this.futureChequesPanelOpen = !this.futureChequesPanelOpen;
    if (this.futureChequesPanelOpen && this.allFutureCheques.length === 0) {
      this.loadFutureCheques();
    }
  }

  loadFutureCheques(): void {
    this.futureChequeLoading = this.allFutureCheques.length === 0;
    this.futureChequeRefreshing = this.allFutureCheques.length > 0;
    this.cdr.detectChanges();
    this.paymentService.getFutureCheques().subscribe({
      next: (res) => {
        this.allFutureCheques = res;
        this.applyFutureFilter();
        this.futureChequeLoading = false;
        this.futureChequeRefreshing = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.futureChequeLoading = false;
        this.futureChequeRefreshing = false;
        this.cdr.detectChanges();
      },
    });
  }

  onFutureCustomerSearch(): void {
    this.applyFutureFilter();
  }

  clearFutureCustomer(): void {
    this.futureCustomerQuery = '';
    this.applyFutureFilter();
  }

  private applyFutureFilter(): void {
    const q = this.futureCustomerQuery.toLowerCase().trim();
    this.futureCheques = q
      ? this.allFutureCheques.filter(p => p.customerName.toLowerCase().includes(q))
      : this.allFutureCheques.slice();
    this.cdr.detectChanges();
  }

  deletePayment(payment: PaymentResponse): void {
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Delete Payment',
        message: `Delete payment of Rs ${payment.paymentAmount} for ${payment.billNumber}? This cannot be undone.`,
        confirmText: 'Delete',
        confirmColor: 'warn',
      },
      maxWidth: '95vw',
    }).afterClosed().subscribe(result => {
      if (!result?.confirmed) return;
      this.paymentService.deletePayment(payment.id).subscribe({
        next: () => this.load(),
        error: () => this.load(),
      });
    });
  }     
  
  
  returnCheque(payment: PaymentResponse): void {
    const ref = this.dialog.open(ReturnChequeDialog, {
      data: {
        paymentId: payment.id,
        billNumber: payment.billNumber
      }, 
      width: '460px',
      disableClose: true
    });

    ref.afterClosed().subscribe(confirmed => {
      if (confirmed) this.load();
    })
  }





}
