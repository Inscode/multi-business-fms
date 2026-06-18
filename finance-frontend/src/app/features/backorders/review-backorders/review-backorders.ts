import { Component, Input, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatChipsModule } from '@angular/material/chips';
import { BackorderRequest, BackorderService } from '../../../core/services/backorder';

@Component({
  selector: 'app-review-backorders',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    MatIconModule,
    MatTableModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatChipsModule,
    DecimalPipe,
  ],
  templateUrl: './review-backorders.html',
  styleUrl: './review-backorders.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReviewBackorders implements OnInit {
  @Input() readonly = false;

  requests: BackorderRequest[] = [];
  filtered: BackorderRequest[] = [];
  filterStatus = 'ALL';
  expandedId: number | null = null;
  loading = false;

  rejectingId: number | null = null;
  rejectReason = '';

  approving: number | null = null;
  rejecting: number | null = null;

  itemColumns = ['product', 'qty', 'unitPrice', 'lineTotal', 'amountToAdd', 'stock'];

  constructor(
    private backorderService: BackorderService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.cdr.detectChanges();
    this.backorderService.getAll().subscribe({
      next: data => {
        this.requests = data;
        this.applyFilter();
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  applyFilter(): void {
    this.filtered = this.filterStatus === 'ALL'
      ? [...this.requests]
      : this.requests.filter(r => r.status === this.filterStatus);
    this.cdr.detectChanges();
  }

  toggleExpand(id: number): void {
    this.expandedId = this.expandedId === id ? null : id;
    this.cdr.detectChanges();
  }

  approve(r: BackorderRequest): void {
    if (!confirm(`Approve backorder for ${r.billNumber} — ${r.customerName}? This will deduct stock and update the bill.`)) return;
    this.approving = r.id;
    this.cdr.detectChanges();
    this.backorderService.approve(r.id).subscribe({
      next: () => {
        this.approving = null;
        this.load();
      },
      error: err => {
        alert(err?.error?.message ?? 'Failed to approve backorder.');
        this.approving = null;
        this.cdr.detectChanges();
      },
    });
  }

  openReject(r: BackorderRequest): void {
    this.rejectingId = r.id;
    this.rejectReason = '';
    this.cdr.detectChanges();
  }

  cancelReject(): void {
    this.rejectingId = null;
    this.rejectReason = '';
    this.cdr.detectChanges();
  }

  confirmReject(): void {
    if (this.rejectingId === null) return;
    this.rejecting = this.rejectingId;
    this.cdr.detectChanges();
    this.backorderService.reject(this.rejectingId, this.rejectReason).subscribe({
      next: () => {
        this.rejecting = null;
        this.rejectingId = null;
        this.rejectReason = '';
        this.load();
      },
      error: err => {
        alert(err?.error?.message ?? 'Failed to reject backorder.');
        this.rejecting = null;
        this.cdr.detectChanges();
      },
    });
  }

  statusLabel(status: string): string {
    return { PENDING: 'Pending', APPROVED: 'Approved', REJECTED: 'Rejected' }[status] ?? status;
  }

  statusClass(status: string): string {
    return { PENDING: 'status-pending', APPROVED: 'status-approved', REJECTED: 'status-rejected' }[status] ?? '';
  }
}
