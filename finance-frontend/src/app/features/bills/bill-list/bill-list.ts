import { CommonModule, DecimalPipe, LowerCasePipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
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
import { RouterLink } from '@angular/router';
import { SelectionModel } from '@angular/cdk/collections';
import { Worker, WorkerResponse } from '../../../core/services/worker';
import { Bill, BillResponse } from '../../../core/services/bill';
import { Auth } from '../../../core/services/auth';
import { BulkPaymentDialog } from '../../payments/bulk-payment-dialog/bulk-payment-dialog';
import { CollectionNoteService, CollectionNoteResponse } from '../../../core/services/collection-note';
import { BillReminderResponse, BillReminderService } from '../../../core/services/bill-reminder';
import { ReminderDialog } from '../reminder-dialog/reminder-dialog';
import { Router } from '@angular/router';

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
    DecimalPipe,
    LowerCasePipe,
    MatInput,
  ],
  templateUrl: './bill-list.html',
  styleUrl: './bill-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BillList implements OnInit {
  bills: any[] = [];
  filteredBills: any[] = [];
  workers: WorkerResponse[] = [];
  loading = true;
  error = false;

  searchQuery = '';
  selectedBusiness = '';
  selectedStatus = '';
  selectedArea = '';

  businesses = ['', 'RAINCO', 'RETAIL_SHOP', 'PLASTIC', 'HARDWARE', 'STATIONERY'];
  statuses = ['', 'CREATED', 'ASSIGNED', 'SHOP_WORKER_ASSIGNED',
              'SHOP_RECEIVED', 'STORE_RECEIVED', 'COMPLETED', 'CANCELLED'];

  areas = [
    'Badalkumbura', 'Badulla', 'Bandarawela', 'Beragala',
    'Bogakumbura', 'Boralanda', 'Diyatalawa', 'Ella',
    'Etampitiya', 'Haldummulla', 'Hali-Ela', 'Haputale',
    'Kandaketiya', 'Kumbalwela', 'Lunugala', 'Mahiyanganaya',
    'Meegahakivula', 'Passara', 'Uva-Paranagama', 'Welimada',
  ];

  selection = new SelectionModel<any>(true, []);

  displayedColumns: string[];

  get isOwner(): boolean { return this.auth.getRole() === 'OWNER'; }

  get canEnterPayment(): boolean {
    return ['ACCOUNTANT', 'MAIN_ACCOUNTANT', 'SHOP_ACCOUNTANT'].includes(this.auth.getRole() ?? '');
  }

  get canMarkShopReceivedRole(): boolean {
    return ['ACCOUNTANT', 'MAIN_ACCOUNTANT'].includes(this.auth.getRole() ?? '');
  }

  get hasBulkSelection(): boolean { return this.selection.selected.length >= 2; }

  isSelectable(b: any): boolean { return !b.fullyPaid && b.status !== 'CANCELLED'; }

  pendingCollections: CollectionNoteResponse[] = [];

  get canSeePendingCollections(): boolean {
    return ['ACCOUNTANT', 'MAIN_ACCOUNTANT'].includes(this.auth.getRole() ?? '');
  }

  reminderMap = new Map<number, BillReminderResponse>();

  getReminderForBill(billId: number): BillReminderResponse | null {
    return this.reminderMap.get(billId) ?? null;
  }

  get canSetReminder(): boolean {
    return ['ACCOUNTANT', 'MAIN_ACCOUNTANT'].includes(this.auth.getRole() ?? '');
  }

  get today(): string {
    return new Date().toISOString().split('T')[0];
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
  ) {
    this.displayedColumns = this.canEnterPayment
      ? ['select', 'billNumber', 'customerName', 'area', 'business', 'totalAmount', 'balanceRemaining', 'workerName', 'status', 'actions']
      : ['billNumber', 'customerName', 'area', 'business', 'totalAmount', 'balanceRemaining', 'workerName', 'status', 'actions'];
  }

  ngOnInit(): void {
    this.load();
    this.loadWorkers();
    if (this.canSeePendingCollections) this.loadPendingCollections();
    if (this.canSetReminder) this.loadReminderMap();
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

  load(): void {
    this.loading = true;
    this.error = false;
    this.selection.clear();
    this.billService.getBills({
      business: this.selectedBusiness || undefined,
      status:   this.selectedStatus   || undefined,
    }).subscribe({
      next: (b) => {
        this.bills = b;
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
      next: (w) => this.workers = w.filter(w => w.active),
      error: () => this.workers = [],
    });
  }

  applyFilters(): void {
    const query = this.searchQuery.toLowerCase().trim();
    this.filteredBills = this.bills.filter(b => {
      const matchesSearch = !query ||
        b.customerName.toLowerCase().includes(query) ||
        (b.billNumber ?? '').toLowerCase().includes(query);
      const matchesArea = !this.selectedArea || b.area === this.selectedArea;
      return matchesSearch && matchesArea;
    });
  }

  onSearchChange(): void { this.applyFilters(); }
  onAreaChange(): void   { this.applyFilters(); }

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

  assignBill(billId: number, workerId: number): void {
    this.billService.assignBill(billId, workerId).subscribe({
      next: () => this.load(),
      error: () => alert('Failed to assign bill.'),
    });
  }

  markReceived(billId: number): void {
    this.billService.markReceived(billId).subscribe({
      next: () => this.load(),
      error: () => alert('Failed to mark as received.'),
    });
  }

  markShopReceived(billId: number): void {
    this.billService.markShopReceived(billId).subscribe({
      next: () => this.load(),
      error: () => alert('Failed to mark as shop received.'),
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

  hasActions(bill: BillResponse): boolean {
    return !this.isOwner && (
      this.canAssign(bill) ||
      this.canMarkStoreReceived(bill) ||
      this.canMarkShopReceived(bill) ||
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