import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Auth } from '../../core/services/auth';
import { Bill } from '../../core/services/bill';
import { WorkerPortalService, WorkerBill, WorkerPaymentEntry, VisitStatus } from '../../core/services/worker-portal';

@Component({
  selector: 'app-worker-collections-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule, FormsModule, DatePipe, DecimalPipe,
    MatCardModule, MatButtonModule, MatIconModule,
    MatProgressSpinnerModule, MatTabsModule, MatTooltipModule,
  ],
  templateUrl: './worker-collections-page.html',
  styleUrl: './worker-collections-page.scss',
})
export class WorkerCollectionsPage implements OnInit {
  entries: WorkerPaymentEntry[] = [];
  bills: WorkerBill[] = [];
  loading = false;
  rejectingId: number | null = null;
  rejectReason = '';
  showRejectInput: number | null = null;

  togglingBillId: number | null = null;
  deletingEntryId: number | null = null;

  get isOwnerOrAdmin(): boolean {
    return ['ADMIN', 'OWNER'].includes(this.auth.getRole() ?? '');
  }

  get canToggleCollection(): boolean {
    return ['ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT'].includes(this.auth.getRole() ?? '');
  }

  get pendingEntries(): WorkerPaymentEntry[] {
    return this.entries.filter(e => e.status === 'PENDING');
  }

  get confirmedEntries(): WorkerPaymentEntry[] {
    return this.entries.filter(e => e.status === 'OWNER_CONFIRMED');
  }

  constructor(
    private svc: WorkerPortalService,
    private billService: Bill,
    public auth: Auth,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.svc.getAllCollections().subscribe({
      next: entries => {
        this.entries = entries;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => { this.loading = false; this.cdr.detectChanges(); },
    });
    this.svc.getWorkerBills().subscribe({
      next: bills => { this.bills = bills; this.cdr.detectChanges(); },
      error: () => {},
    });
  }

  confirm(entryId: number): void {
    this.svc.confirmEntry(entryId).subscribe({
      next: () => this.load(),
      error: () => alert('Failed to confirm'),
    });
  }

  startReject(entryId: number): void {
    this.showRejectInput = entryId;
    this.rejectReason = '';
    this.cdr.detectChanges();
  }

  submitReject(entryId: number): void {
    if (!this.rejectReason.trim()) return;
    this.svc.rejectEntry(entryId, this.rejectReason).subscribe({
      next: () => { this.showRejectInput = null; this.load(); },
      error: () => alert('Failed to reject'),
    });
  }

  cancelReject(): void {
    this.showRejectInput = null;
    this.cdr.detectChanges();
  }

  hardDelete(entryId: number): void {
    if (!confirm('Delete this worker collection entry? If already confirmed, the bill balance will be restored.')) return;
    this.deletingEntryId = entryId;
    this.cdr.detectChanges();
    this.svc.hardDeleteEntry(entryId).subscribe({
      next: () => { this.deletingEntryId = null; this.load(); },
      error: () => { this.deletingEntryId = null; alert('Failed to delete entry'); this.cdr.detectChanges(); },
    });
  }

  toggleCollectionOnly(bill: WorkerBill): void {
    this.togglingBillId = bill.id;
    this.cdr.detectChanges();
    this.billService.toggleCollectionOnly(bill.id).subscribe({
      next: () => {
        bill.collectionOnly = !bill.collectionOnly;
        this.togglingBillId = null;
        this.cdr.detectChanges();
      },
      error: () => {
        this.togglingBillId = null;
        this.cdr.detectChanges();
      },
    });
  }

  visitColor(status: VisitStatus): string {
    return ({
      NOT_VISITED: '#bdbdbd', SHOP_CLOSED: '#ef5350',
      REVISIT_REQUESTED: '#ff9800', DELIVERED_PENDING: '#1976d2',
      DELIVERED_PAID: '#43a047',
    } as any)[status] ?? '#9e9e9e';
  }

  visitLabel(status: VisitStatus): string {
    return ({
      NOT_VISITED: 'Not Visited', SHOP_CLOSED: 'Shop Closed',
      REVISIT_REQUESTED: 'Come Later', DELIVERED_PENDING: 'Delivered — Awaiting Payment',
      DELIVERED_PAID: 'Delivered & Paid',
    } as any)[status] ?? status;
  }
}
