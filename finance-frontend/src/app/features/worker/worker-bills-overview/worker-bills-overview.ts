import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { WorkerPortalService, WorkerBillOverview } from '../../../core/services/worker-portal';
import { WorkerTranslateService } from '../../../core/services/worker-translate';

const PENDING_STATUSES = ['CREATED', 'ASSIGNED', 'SHOP_WORKER_ASSIGNED', 'STORE_RECEIVED', 'SHOP_RECEIVED'];

const STATUS_LABELS: Record<string, string> = {
  CREATED: 'Created',
  ASSIGNED: 'Worker Assigned',
  SHOP_WORKER_ASSIGNED: 'Shop Worker Assigned',
  STORE_RECEIVED: 'Store Received',
  SHOP_RECEIVED: 'Shop Received',
  COMPLETED: 'Completed',
};

const STATUS_COLORS: Record<string, string> = {
  CREATED: '#bdbdbd',
  ASSIGNED: '#1976d2',
  SHOP_WORKER_ASSIGNED: '#7b1fa2',
  STORE_RECEIVED: '#f57c00',
  SHOP_RECEIVED: '#0288d1',
  COMPLETED: '#43a047',
};

@Component({
  selector: 'app-worker-bills-overview',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, MatIconModule, MatProgressSpinnerModule, DecimalPipe],
  templateUrl: './worker-bills-overview.html',
  styleUrl: './worker-bills-overview.scss',
})
export class WorkerBillsOverview implements OnInit {
  all: WorkerBillOverview[] = [];
  filtered: WorkerBillOverview[] = [];
  areas: string[] = [];

  loading = false;
  error = false;

  view: 'pending' | 'completed' = 'pending';
  searchQuery = '';
  areaFilter = '';
  statusFilter = '';
  typeFilter: '' | 'new' | 'collection' = '';

  get pendingStatuses() { return PENDING_STATUSES; }

  constructor(
    public tr: WorkerTranslateService,
    private svc: WorkerPortalService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.error = false;
    this.cdr.detectChanges();
    this.svc.getBillsOverview(this.view).subscribe({
      next: bills => {
        this.all = bills;
        this.areas = [...new Set(bills.map(b => b.area).filter((a): a is string => !!a))].sort();
        this.applyFilters();
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

  switchView(v: 'pending' | 'completed'): void {
    if (this.view === v) return;
    this.view = v;
    this.statusFilter = '';
    this.areaFilter = '';
    this.searchQuery = '';
    this.typeFilter = '';
    this.load();
  }

  applyFilters(): void {
    const q = this.searchQuery.toLowerCase().trim();
    this.filtered = this.all.filter(b => {
      const matchSearch = !q || b.customerName.toLowerCase().includes(q) || b.billNumber.toLowerCase().includes(q);
      const matchArea = !this.areaFilter || b.area === this.areaFilter;
      const matchStatus = !this.statusFilter || b.status === this.statusFilter;
      const matchType = !this.typeFilter
        || (this.typeFilter === 'new' && this.isNew(b))
        || (this.typeFilter === 'collection' && !this.isNew(b));
      return matchSearch && matchArea && matchStatus && matchType;
    });
    this.cdr.detectChanges();
  }

  isNew(b: WorkerBillOverview): boolean {
    if (b.collectionOnly) return false;
    if (!b.createdAt) return false;
    const created = new Date(b.createdAt);
    const diffMs = Date.now() - created.getTime();
    return diffMs <= 3 * 24 * 60 * 60 * 1000;
  }

  setTypeFilter(t: '' | 'new' | 'collection'): void {
    this.typeFilter = t;
    this.applyFilters();
  }

  clearSearch(): void {
    this.searchQuery = '';
    this.applyFilters();
  }

  statusLabel(s: string): string { return STATUS_LABELS[s] ?? s; }
  statusColor(s: string): string { return STATUS_COLORS[s] ?? '#9e9e9e'; }

  statusIcon(s: string): string {
    const map: Record<string, string> = {
      CREATED: 'fiber_new',
      ASSIGNED: 'local_shipping',
      SHOP_WORKER_ASSIGNED: 'store',
      STORE_RECEIVED: 'inventory',
      SHOP_RECEIVED: 'shopping_bag',
      COMPLETED: 'check_circle',
    };
    return map[s] ?? 'receipt_long';
  }
}
