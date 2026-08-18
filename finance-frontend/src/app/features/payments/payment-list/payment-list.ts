import { CommonModule, DatePipe, DecimalPipe, LowerCasePipe } from '@angular/common';
import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, HostListener, OnDestroy, OnInit, ViewChild } from '@angular/core';
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
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ChequeAgeBand, chequeAgeBand, chequeAgeDays, chequeAgeLabel, chequeAgeTooltip } from '../../../core/utils/cheque-age';
import { BANKS } from '../../../core/constants/payment-options';
import { Payment, PaymentResponse } from '../../../core/services/payment';
import { Auth } from '../../../core/services/auth';
import { Router, RouterLink } from '@angular/router';
import { ReturnChequeDialog } from '../return-cheque-dialog/return-cheque-dialog';
import { PaymentPhotoDialog } from '../payment-photo-dialog/payment-photo-dialog';
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
    MatTabsModule,
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

  // Cheque numbers repeat across banks, so a number alone can match several customers.
  chequeBankFilter = '';
  banks = BANKS;

  get chequeSearchFiltered(): PaymentResponse[] {
    if (!this.chequeBankFilter) return this.chequeSearchResults;
    return this.chequeSearchResults.filter(p => p.bankName === this.chequeBankFilter);
  }

  /** Banks present in the current cheque-number results — the ones worth disambiguating. */
  get chequeSearchBanks(): string[] {
    return [...new Set(this.chequeSearchResults.map(p => p.bankName).filter((b): b is string => !!b))].sort();
  }

  // ── Cheque Payments tab ────────────────────────────────────────
  // Default view is future-dated cheques only. A customer search widens the net to
  // the previous 3 months as well, so past cheques from the same customer surface.
  private static readonly RECENT_MONTHS = 3;

  private allFutureCheques: PaymentResponse[] = [];
  private recentCheques: PaymentResponse[] = [];   // last 3 months, loaded on first search
  private recentLoaded = false;
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

  /**
   * Narrow enough that the tables have to lose columns rather than be scrolled to.
   *
   * <p>A nine-column table on a phone is 720px of sideways scrolling to read one row,
   * with the bill number — the only thing identifying it — off screen by the third
   * column. Fewer columns is worse at a desk and far better in a hand, so which set is
   * used follows the width.
   */
  isNarrow = window.innerWidth <= 700;

  @HostListener('window:resize')
  onResize(): void {
    const narrow = window.innerWidth <= 700;
    if (narrow !== this.isNarrow) {
      this.isNarrow = narrow;
      this.cdr.markForCheck();
    }
  }

  private readonly allColumns = ['billNumber', 'customerName', 'amount',
                      'type', 'chequeAge', 'enteredBy', 'date', 'status', 'actions'];

  // Who paid, how much, where it stands, and what to do about it. Type, age, who
  // entered it and when are all answers to questions nobody asks standing up.
  private readonly narrowColumns = ['billNumber', 'customerName', 'amount',
                      'status', 'actions'];

  get displayedColumns(): string[] {
    return this.isNarrow ? this.narrowColumns : this.allColumns;
  }

  private readonly allChequeColumns = ['billNumber', 'customerName', 'chequeNumber', 'bank',
                   'amount', 'billDate', 'chequeDate', 'when', 'chequeAge', 'status'];

  // A cheque is chased by its number and its date; the bank and the bill dates are
  // detail for when one is actually in dispute.
  private readonly narrowChequeColumns = ['chequeNumber', 'customerName', 'amount',
                   'chequeDate', 'status'];

  get chequeColumns(): string[] {
    return this.isNarrow ? this.narrowChequeColumns : this.allChequeColumns;
  }

  // ── Cheque age (bill date → cheque date) ───────────────────────
  chequeBandFilter: 'ALL' | ChequeAgeBand = 'ALL';

  ageDays(p: PaymentResponse): number | null {
    if (p.paymentType !== 'CHEQUE') return null;
    return chequeAgeDays(p.billDate, p.chequeDate);
  }

  ageBand(days: number): ChequeAgeBand { return chequeAgeBand(days); }
  ageLabel(days: number): string { return chequeAgeLabel(days); }
  ageTooltip(days: number): string { return chequeAgeTooltip(days); }

  /**
   * Future-dated cheques, plus — while a customer search is active — that customer's
   * cheques from the previous 3 months. Deduped, newest cheque date first.
   */
  private get chequeBase(): PaymentResponse[] {
    const query = this.futureCustomerQuery.toLowerCase().trim();
    if (!query) return this.allFutureCheques;

    const matches = (p: PaymentResponse) => p.customerName.toLowerCase().includes(query);
    const byId = new Map<number, PaymentResponse>();
    this.allFutureCheques.filter(matches).forEach(p => byId.set(p.id, p));
    this.recentCheques.filter(matches).forEach(p => { if (!byId.has(p.id)) byId.set(p.id, p); });

    return [...byId.values()].sort((a, b) => (b.chequeDate ?? '').localeCompare(a.chequeDate ?? ''));
  }

  /** The cheque rows on screen, narrowed by the active age band. */
  get chequePayments(): PaymentResponse[] {
    const cheques = this.chequeBase;
    if (this.chequeBandFilter === 'ALL') return cheques;
    return cheques.filter(p => {
      const days = this.ageDays(p);
      return days !== null && chequeAgeBand(days) === this.chequeBandFilter;
    });
  }

  bandCount(band: ChequeAgeBand): number {
    return this.chequeBase.filter(p => {
      const days = this.ageDays(p);
      return days !== null && chequeAgeBand(days) === band;
    }).length;
  }

  get chequeCount(): number { return this.chequeBase.length; }

  get chequeTotal(): number {
    return this.chequeBase.reduce((sum, p) => sum + p.paymentAmount, 0);
  }

  /** A cheque dated today or later is still to clear. */
  isFutureCheque(p: PaymentResponse): boolean {
    return !!p.chequeDate && this.daysUntil(p.chequeDate) >= 0;
  }

  whenLabel(p: PaymentResponse): string {
    if (!p.chequeDate) return '—';
    const days = this.daysUntil(p.chequeDate);
    if (days === 0) return 'Today';
    return days > 0 ? `In ${days}d` : `${Math.abs(days)}d ago`;
  }

  setBandFilter(band: 'ALL' | ChequeAgeBand): void {
    this.chequeBandFilter = band;
    this.cdr.detectChanges();
  }

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

  /**
   * Confirming shows the photo the accountant attached, and offers the admin their own.
   * Put in front of them here rather than on a detail page: confirming without seeing
   * the evidence would make requiring it pointless.
   */
  confirmPayment(payment: PaymentResponse): void {
    this.dialog.open(PaymentPhotoDialog, {
      data: { payment, mode: 'confirm' },
      width: '520px',
      maxWidth: '95vw',
    }).afterClosed().subscribe(result => {
      if (!result?.confirmed) return;
      this.paymentService.confirmPayment(payment.id, result.confirmImageUrl).subscribe({
        next: () => this.load(),
        error: () => this.load(),
      });
    });
  }

  /** Read-only view, so an accountant can see what an admin recorded and the reverse. */
  viewPhotos(payment: PaymentResponse): void {
    this.dialog.open(PaymentPhotoDialog, {
      data: { payment, mode: 'view' },
      width: '520px',
      maxWidth: '95vw',
    });
  }

  hasPhoto(payment: PaymentResponse): boolean {
    return !!payment.receiptImageUrl || !!payment.confirmImageUrl;
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

  // ── Cheque Payments tab ──────────────────────────────────────────────────
  onTabChange(index: number): void {
    if (index === 1 && this.allFutureCheques.length === 0) this.loadFutureCheques();
  }

  loadFutureCheques(): void {
    this.futureChequeLoading = this.allFutureCheques.length === 0;
    this.futureChequeRefreshing = this.allFutureCheques.length > 0;
    this.cdr.detectChanges();
    this.paymentService.getFutureCheques().subscribe({
      next: (res) => {
        this.allFutureCheques = res;
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
    // Searching a customer widens the window to the last 3 months, fetched once.
    if (this.futureCustomerQuery.trim() && !this.recentLoaded) this.loadRecentCheques();
    this.cdr.detectChanges();
  }

  clearFutureCustomer(): void {
    this.futureCustomerQuery = '';
    this.cdr.detectChanges();
  }

  private loadRecentCheques(): void {
    this.recentLoaded = true;
    const from = new Date();
    from.setMonth(from.getMonth() - PaymentList.RECENT_MONTHS);
    this.paymentService.getAllPayments(undefined, localDateStr(from), this.today).subscribe({
      next: (res) => {
        this.recentCheques = res.filter(p => p.paymentType === 'CHEQUE');
        this.cdr.detectChanges();
      },
      error: () => { this.recentLoaded = false; this.cdr.detectChanges(); },
    });
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
