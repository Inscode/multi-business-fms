import { CommonModule, DecimalPipe, LowerCasePipe } from '@angular/common';
import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { localDateStr } from '../../../core/utils/date-utils';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatInput } from '@angular/material/input';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator';
import { MatTableDataSource } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatTabsModule } from '@angular/material/tabs';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { RouterLink } from '@angular/router';
import { SelectionModel } from '@angular/cdk/collections';
import { Worker, WorkerResponse } from '../../../core/services/worker';
import { Bill, BillResponse, BillSequenceGap } from '../../../core/services/bill';
import { Auth } from '../../../core/services/auth';
import { LinkingBillsTab } from '../linking-bills-tab/linking-bills-tab';
import { BulkPaymentDialog } from '../../payments/bulk-payment-dialog/bulk-payment-dialog';
import { BulkAssignDialog } from '../bulk-assign-dialog/bulk-assign-dialog';
import { ConfirmDialog } from '../../../shared/confirm-dialog/confirm-dialog';
import { CollectionNoteService, CollectionNoteResponse } from '../../../core/services/collection-note';
import { BillReminderResponse, BillReminderService } from '../../../core/services/bill-reminder';
import { ReminderDialog } from '../reminder-dialog/reminder-dialog';
import { Router } from '@angular/router';
import { Subject, of } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap, catchError } from 'rxjs/operators';
import { BillFilterState } from '../../../core/services/bill-filter-state';

@Component({
  selector: 'app-bill-list',
  imports: [
    CommonModule,
    RouterLink,
    FormsModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatFormFieldModule,
    MatProgressSpinnerModule,
    MatMenuModule,
    MatDialogModule,
    MatCheckboxModule,
    MatTooltipModule,
    MatPaginatorModule,
    MatDatepickerModule,
    MatNativeDateModule,
    DecimalPipe,
    LowerCasePipe,
    MatInput,
    MatTabsModule,
    MatSlideToggleModule,
    LinkingBillsTab,
  ],
  templateUrl: './bill-list.html',
  styleUrl: './bill-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BillList implements OnInit, AfterViewInit, OnDestroy {
  dataSource = new MatTableDataSource<BillResponse>([]);
  private allBills: BillResponse[] = [];
  workers: WorkerResponse[] = [];
  loading = true;
  error = false;

  @ViewChild(MatPaginator) paginator!: MatPaginator;

  searchQuery = '';
  selectedBusiness = '';
  selectedStatus = '';
  selectedArea = '';
  hideCompleted = true;

  // ── Global search ──────────────────────────────────────────────
  globalSearchMode = false;
  globalResults: BillResponse[] = [];
  globalSearching = false;
  globalDropdownOpen = false;
  private globalSearch$ = new Subject<string>();

  // ── Date filter ────────────────────────────────────────────────
  filterFrom: string = this.daysAgo(60);
  filterTo: string   = this.today;

  // ── Overdue mode ───────────────────────────────────────────────
  overdueMode = false;
  overdueCount = 0;

  get businesses(): string[] {
    const role = this.auth.getRole();
    if (role === 'SHOP_ACCOUNTANT') return ['RETAIL_SHOP'];
    if (role === 'ACCOUNTANT' || role === 'MAIN_ACCOUNTANT')
      return ['', 'RAINCO', 'STATIONERY', 'PLASTIC', 'HARDWARE'];
    return ['', 'RAINCO', 'RETAIL_SHOP', 'STATIONERY', 'PLASTIC', 'HARDWARE']; // ADMIN / OWNER
  }

  get isShopAccountant(): boolean { return this.auth.getRole() === 'SHOP_ACCOUNTANT'; }

  statuses = ['', 'CREATED', 'ASSIGNED', 'SHOP_WORKER_ASSIGNED',
              'SHOP_RECEIVED', 'STORE_RECEIVED', 'COMPLETED', 'CANCELLED'];

  areas: string[] = [];

  selection = new SelectionModel<any>(true, []);

  displayedColumns: string[];

  get isOwner(): boolean { return this.auth.getRole() === 'OWNER'; }
  get isAdmin(): boolean { return this.auth.getRole() === 'ADMIN'; }

  get canEnterPayment(): boolean {
    return ['ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT', 'SHOP_ACCOUNTANT'].includes(this.auth.getRole() ?? '');
  }

  get canMarkShopReceivedRole(): boolean {
    return ['ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT'].includes(this.auth.getRole() ?? '');
  }

  get hasBulkSelection(): boolean { return this.selection.selected.length >= 2; }

  isSelectable(b: any): boolean { return !b.fullyPaid && b.status !== 'CANCELLED'; }

  get canBulkAssign(): boolean {
    return this.hasBulkSelection &&
      this.selection.selected.every((b: BillResponse) => this.canAssign(b));
  }

  get canBulkMarkReceived(): boolean {
    return this.hasBulkSelection &&
      this.selection.selected.every((b: BillResponse) => this.canMarkStoreReceived(b));
  }

  get canBulkMarkShopReceived(): boolean {
    return this.hasBulkSelection &&
      this.selection.selected.every((b: BillResponse) => this.canMarkShopReceived(b));
  }

  pendingCollections: CollectionNoteResponse[] = [];
  collectionsExpanded = false;

  get canSeePendingCollections(): boolean {
    return ['ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT'].includes(this.auth.getRole() ?? '');
  }

  // ── Sequence gap check ─────────────────────────────────────────
  gapCheckOpen = false;
  gapCheckLoading = false;
  gaps: BillSequenceGap[] = [];

  get canCheckGaps(): boolean {
    return this.auth.getRole() === 'ADMIN';
  }

  toggleGapCheck(): void {
    if (this.gapCheckOpen) { this.gapCheckOpen = false; return; }
    this.gapCheckOpen = true;
    this.loadGaps();
  }

  loadGaps(): void {
    this.gapCheckLoading = true;
    this.billService.findSequenceGaps('RAINCO').subscribe({
      next: (g) => { this.gaps = g; this.gapCheckLoading = false; this.cdr.detectChanges(); },
      error: () => { this.gapCheckLoading = false; this.cdr.detectChanges(); },
    });
  }

  reminderMap = new Map<number, BillReminderResponse>();

  getReminderForBill(billId: number): BillReminderResponse | null {
    return this.reminderMap.get(billId) ?? null;
  }

  get canSetReminder(): boolean {
    return ['ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT'].includes(this.auth.getRole() ?? '');
  }

  constructor(
    private billService: Bill,
    private workerService: Worker,
    private cdr: ChangeDetectorRef,
    private auth: Auth,
    private dialog: MatDialog,
    private collectionNoteService: CollectionNoteService,
    private reminderService: BillReminderService,
    private router: Router,
    private filterState: BillFilterState,
  ) {
    if (this.auth.getRole() === 'SHOP_ACCOUNTANT') {
      this.selectedBusiness = 'RETAIL_SHOP';
    }
    this.displayedColumns = this.canEnterPayment
      ? ['select', 'billNumber', 'customerName', 'area', 'business', 'billType', 'totalAmount', 'balanceRemaining', 'workerName', 'status', 'actions']
      : ['billNumber', 'customerName', 'area', 'business', 'billType', 'totalAmount', 'balanceRemaining', 'workerName', 'status', 'actions'];
  }

  get today(): string { return localDateStr(); }

  daysAgo(n: number): string {
    const d = new Date();
    d.setDate(d.getDate() - n);
    return localDateStr(d);
  }

  // Date objects for Material datepicker bindings
  get filterFromDate(): Date | null { return this.filterFrom ? new Date(this.filterFrom + 'T00:00:00') : null; }
  get filterToDate():   Date | null { return this.filterTo   ? new Date(this.filterTo   + 'T00:00:00') : null; }
  get todayDate(): Date { return new Date(); }

  onFromDateChange(e: { value: Date | null }): void {
    this.filterFrom = e.value ? this.formatDate(e.value) : '';
    this.overdueMode = false;
    this.load();
  }

  onToDateChange(e: { value: Date | null }): void {
    this.filterTo = e.value ? this.formatDate(e.value) : '';
    this.overdueMode = false;
    this.load();
  }

  private formatDate(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  ngOnDestroy(): void {
    this.globalSearch$.complete();
    this.filterState.save({
      filterFrom: this.filterFrom,
      filterTo: this.filterTo,
      selectedBusiness: this.selectedBusiness,
      selectedStatus: this.selectedStatus,
      selectedArea: this.selectedArea,
      hideCompleted: this.hideCompleted,
      overdueMode: this.overdueMode,
      searchQuery: this.searchQuery,
      pageIndex: this.paginator?.pageIndex ?? 0,
    });
  }

  ngAfterViewInit(): void {
    this.dataSource.paginator = this.paginator;
    const saved = this.filterState.restore();
    if (saved && this.paginator) {
      this.paginator.pageIndex = saved.pageIndex;
    }
  }

  ngOnInit(): void {
    const saved = this.filterState.restore();
    if (saved) {
      this.filterFrom       = saved.filterFrom;
      this.filterTo         = saved.filterTo;
      this.selectedBusiness = saved.selectedBusiness;
      this.selectedStatus   = saved.selectedStatus;
      this.selectedArea     = saved.selectedArea;
      this.hideCompleted    = saved.hideCompleted;
      this.overdueMode      = saved.overdueMode;
      this.searchQuery      = saved.searchQuery;
    }
    this.load();
    this.loadWorkers();
    this.loadOverdueCount();
    if (this.canSeePendingCollections) this.loadPendingCollections();
    if (this.canSetReminder) this.loadReminderMap();

    this.globalSearch$.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap(q => {
        if (!q || q.length < 2) {
          this.globalResults = [];
          this.globalDropdownOpen = false;
          this.globalSearching = false;
          this.cdr.detectChanges();
          return of([]);
        }
        this.globalSearching = true;
        this.cdr.detectChanges();
        return this.billService.globalSearch(q).pipe(
          catchError(() => {
            this.globalSearching = false;
            this.cdr.detectChanges();
            return of([]);
          })
        );
      }),
    ).subscribe({
      next: (results) => {
        this.globalResults = results as BillResponse[];
        this.globalSearching = false;
        this.globalDropdownOpen = this.globalResults.length > 0 || this.searchQuery.length >= 2;
        this.cdr.detectChanges();
      },
    });
  }

  loadPendingCollections(): void {
    this.collectionNoteService.getPendingNotes().subscribe({
      next: (notes) => { this.pendingCollections = notes; this.cdr.detectChanges(); },
      error: (e) => { console.error('collection-notes/pending failed:', e); },
    });
  }

  loadReminderMap(): void {
    this.reminderService.getPending().subscribe({
      next: (reminders) => {
        this.reminderMap.clear();
        reminders.forEach(r => {
          const existing = this.reminderMap.get(r.billId);
          if (!existing || r.reminderDate < existing.reminderDate) {
            this.reminderMap.set(r.billId, r);
          }
        });
        this.cdr.detectChanges();
      },
      error: () => {},
    });
  }

  enterFromCollection(note: CollectionNoteResponse): void {
    this.router.navigate(['/payments/enter'], {
      state: {
        preselectedBill: {
          id: note.billId,
          billNumber: note.billNumber,
          customerName: note.customerName,
          area: note.area,
          balanceRemaining: note.billBalance,
          totalAmount: note.billBalance,
          status: '',
        },
        collectionNoteId: note.id,
        prefillAmount: note.amount,
      },
    });
  }

  toggleHideCompleted(): void {
    this.overdueMode = false;
    this.hideCompleted = !this.hideCompleted;
    this.load();
  }

  toggleOverdueMode(): void {
    this.overdueMode = !this.overdueMode;
    if (this.overdueMode) {
      this.hideCompleted = true;
      this.selectedStatus = '';
    }
    this.load();
  }

  onDateChange(): void {
    this.overdueMode = false;
    this.load();
  }

  loadOverdueCount(): void {
    this.billService.getOverdueCount().subscribe({
      next: (r) => { this.overdueCount = r.count; this.cdr.detectChanges(); },
      error: () => {},
    });
  }

  load(): void {
    this.loading = true;
    this.error = false;
    this.selection.clear();

    const filter: { business?: string; status?: string; excludeCompleted: boolean; from?: string; to?: string } = {
      business:         this.selectedBusiness || undefined,
      excludeCompleted: this.hideCompleted,
    };

    if (this.overdueMode) {
      filter.to = this.daysAgo(60);
      filter.status = undefined;
      filter.excludeCompleted = true;
    } else {
      filter.status = this.selectedStatus || undefined;
      filter.from = this.filterFrom || undefined;
      filter.to   = this.filterTo   || undefined;
    }

    this.billService.getBills(filter).subscribe({
      next: (b) => {
        this.allBills = b;
        this.areas = [...new Set(b.map(bill => bill.area).filter((a): a is string => !!a))].sort();
        // clear area filter if the selected area is no longer in the loaded data
        if (this.selectedArea && !this.areas.includes(this.selectedArea)) {
          this.selectedArea = '';
        }
        this.loading = false;
        this.applyFilters();
        this.cdr.detectChanges();
      },
      error: () => {
        this.error = true;
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  private loadWorkers(): void {
    this.workerService.getAllWorkers().subscribe({
      next: (w) => this.workers = w.filter(w => w.active && w.billAssignable),
      error: () => this.workers = [],
    });
  }

  applyFilters(): void {
    const query = this.searchQuery.toLowerCase().trim();
    const filtered = this.allBills.filter(b => {
      if (b.willBeLinked) return false; // linking bills shown in separate tab
      const matchesSearch = !query ||
        b.customerName.toLowerCase().includes(query) ||
        (b.billNumber ?? '').toLowerCase().includes(query);
      const matchesArea = !this.selectedArea || b.area === this.selectedArea;
      return matchesSearch && matchesArea;
    });
    this.dataSource.data = filtered;
    if (this.paginator) this.paginator.firstPage();
    this.cdr.detectChanges();
  }

  onSearchChange(): void {
    if (this.globalSearchMode) {
      this.globalSearch$.next(this.searchQuery.trim());
    } else {
      this.applyFilters();
    }
  }

  toggleGlobalSearch(): void {
    this.globalSearchMode = !this.globalSearchMode;
    this.searchQuery = '';
    this.globalResults = [];
    this.globalDropdownOpen = false;
    if (!this.globalSearchMode) this.applyFilters();
    this.cdr.detectChanges();
  }

  selectGlobalResult(bill: BillResponse): void {
    this.globalDropdownOpen = false;
    this.router.navigate(['/bills', bill.id]);
  }

  closeGlobalDropdown(): void {
    setTimeout(() => { this.globalDropdownOpen = false; this.cdr.detectChanges(); }, 150);
  }

  onAreaChange(): void { this.applyFilters(); }

  onFilterChange(): void { this.load(); }

  toggleSelection(b: any): void {
    if (this.isSelectable(b)) this.selection.toggle(b);
  }

  openBulkPayment(): void {
    const ref = this.dialog.open(BulkPaymentDialog, {
      data: { bills: this.selection.selected },
      width: '600px',
      maxWidth: '100vw',
      maxHeight: '95vh',
      panelClass: 'bulk-payment-panel',
    });
    ref.afterClosed().subscribe(success => {
      if (success) {
        this.selection.clear();
        this.load();
      }
    });
  }

  openBulkAssign(): void {
    const ref = this.dialog.open(BulkAssignDialog, {
      data: { bills: this.selection.selected, workers: this.workers },
      width: '520px',
      maxWidth: '100vw',
      maxHeight: '95vh',
      panelClass: 'bulk-assign-panel',
    });
    ref.afterClosed().subscribe(success => {
      if (success) {
        this.selection.clear();
        this.load();
      }
    });
  }

  bulkMarkReceived(): void {
    const count = this.selection.selected.length;
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Mark as Store Received',
        message: `Mark ${count} selected bill${count !== 1 ? 's' : ''} as Store Received?`,
        confirmText: 'Mark Received',
        confirmColor: 'primary',
      },
      width: '400px',
    }).afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      const billIds = this.selection.selected.map((b: BillResponse) => b.id);
      this.billService.bulkMarkReceived(billIds).subscribe({
        next: () => { this.selection.clear(); this.load(); },
        error: (err) => this.showError(err?.error?.message ?? 'Failed to mark bills as received.'),
      });
    });
  }

  bulkMarkShopReceived(): void {
    const count = this.selection.selected.length;
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Mark as Shop Received',
        message: `Mark ${count} selected bill${count !== 1 ? 's' : ''} as Shop Received?`,
        confirmText: 'Mark Received',
        confirmColor: 'primary',
      },
      width: '400px',
    }).afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      const billIds = this.selection.selected.map((b: BillResponse) => b.id);
      this.billService.bulkMarkShopReceived(billIds).subscribe({
        next: () => { this.selection.clear(); this.load(); },
        error: (err) => this.showError(err?.error?.message ?? 'Failed to mark bills as shop received.'),
      });
    });
  }

  private showError(message: string): void {
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Error',
        message,
        confirmText: 'Close',
        confirmColor: 'warn',
        hideCancel: true,
      },
      width: '380px',
    });
  }

  assignBill(billId: number, workerId: number): void {
    this.billService.assignBill(billId, workerId).subscribe({
      next: () => this.load(),
      error: (err) => this.showError(err?.error?.message ?? 'Failed to assign bill.'),
    });
  }

  markReceived(billId: number): void {
    this.billService.markReceived(billId).subscribe({
      next: () => this.load(),
      error: (err) => this.showError(err?.error?.message ?? 'Failed to mark as received.'),
    });
  }

  markShopReceived(billId: number): void {
    this.billService.markShopReceived(billId).subscribe({
      next: () => this.load(),
      error: (err) => this.showError(err?.error?.message ?? 'Failed to mark as shop received.'),
    });
  }

  canAssign(bill: BillResponse): boolean {
    return ['CREATED', 'ASSIGNED', 'STORE_RECEIVED'].includes(bill.status) && !bill.fullyPaid;
  }

  canMarkStoreReceived(bill: BillResponse): boolean {
    return ['ASSIGNED', 'SHOP_RECEIVED'].includes(bill.status);
  }

  canMarkShopReceived(bill: BillResponse): boolean {
    return this.canMarkShopReceivedRole &&
      ['CREATED', 'ASSIGNED', 'STORE_RECEIVED'].includes(bill.status);
  }

  canMarkCompleted(bill: BillResponse): boolean {
    return this.isAdmin && bill.status !== 'COMPLETED' && bill.status !== 'CANCELLED';
  }

  markCompleted(bill: BillResponse): void {
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Mark Bill as Completed',
        message:
          `Bill: ${bill.billNumber}\nCustomer: ${bill.customerName}\n\n` +
          `This cannot be undone. Completed bills cannot be assigned, received, or have new payments entered.`,
        confirmText: 'Mark Completed',
        confirmColor: 'primary',
      },
      width: '420px',
    }).afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.billService.markCompleted(bill.id).subscribe({
        next: () => this.load(),
        error: (err) => this.showError(err?.error?.message ?? 'Failed to mark bill as completed.'),
      });
    });
  }

  hasActions(bill: BillResponse): boolean {
    return !this.isOwner && (
      this.canAssign(bill) ||
      this.canMarkStoreReceived(bill) ||
      this.canMarkShopReceived(bill) ||
      this.canMarkCompleted(bill) ||
      this.canSetReminder
    );
  }

  openReminderDialog(bill: BillResponse): void {
    this.dialog.open(ReminderDialog, {
      data: {
        billId:       bill.id,
        billNumber:   bill.billNumber,
        customerName: bill.customerName,
      },
      width: '400px',
    }).afterClosed().subscribe(saved => {
      if (saved) this.loadReminderMap();
    });
  }
}
