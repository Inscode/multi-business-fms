import { Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { FormsModule } from '@angular/forms';
import { Auth } from '../../../core/services/auth';
import { DamageDispatchResponse, DamageDispatchService } from '../../../core/services/damage-dispatch';

@Component({
  selector: 'app-view-damage-dispatches',
  standalone: true,
  imports: [
    CommonModule, DatePipe, DecimalPipe, FormsModule,
    MatButtonModule, MatIconModule, MatProgressSpinnerModule, MatExpansionModule,
    MatFormFieldModule, MatInputModule,
  ],
  templateUrl: './view-damage-dispatches.html',
  styleUrl: './view-damage-dispatches.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ViewDamageDispatches implements OnInit {
  dispatches: DamageDispatchResponse[] = [];
  loading = false;
  expandedId: number | null = null;
  loadingDetail: number | null = null;
  detailCache = new Map<number, DamageDispatchResponse>();

  processing = false;
  errorMsg = '';
  rejectingId: number | null = null;
  rejectReason = '';

  statusFilter: 'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED' = 'ALL';

  constructor(
    private service: DamageDispatchService,
    private auth: Auth,
    private cdr: ChangeDetectorRef,
  ) {}

  get isAdmin(): boolean { return this.auth.getRole() === 'ADMIN'; }

  get filtered(): DamageDispatchResponse[] {
    return this.statusFilter === 'ALL'
      ? this.dispatches
      : this.dispatches.filter(d => d.status === this.statusFilter);
  }

  countByStatus(status: 'PENDING' | 'APPROVED' | 'REJECTED'): number {
    return this.dispatches.filter(d => d.status === status).length;
  }

  setFilter(f: 'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED'): void {
    this.statusFilter = f;
    this.cdr.markForCheck();
  }

  statusClass(status: string): string {
    return ({ PENDING: 'pending', APPROVED: 'approved', REJECTED: 'rejected' } as Record<string, string>)[status] ?? '';
  }

  // ── Approve / reject (admin only) ────────────────────────────────

  approve(d: DamageDispatchResponse): void {
    this.processing = true;
    this.errorMsg = '';
    this.cdr.markForCheck();
    this.service.approve(d.id).subscribe({
      next: () => { this.processing = false; this.detailCache.delete(d.id); this.load(); },
      error: (e) => {
        this.errorMsg = e?.error?.message ?? 'Could not approve this dispatch.';
        this.processing = false;
        this.cdr.markForCheck();
      },
    });
  }

  startReject(id: number): void { this.rejectingId = id; this.rejectReason = ''; this.cdr.markForCheck(); }
  cancelReject(): void { this.rejectingId = null; this.cdr.markForCheck(); }

  confirmReject(): void {
    if (!this.rejectingId || !this.rejectReason.trim()) return;
    this.processing = true;
    this.service.reject(this.rejectingId, this.rejectReason).subscribe({
      next: () => { this.processing = false; this.rejectingId = null; this.load(); },
      error: (e) => {
        this.errorMsg = e?.error?.message ?? 'Could not reject this dispatch.';
        this.processing = false;
        this.cdr.markForCheck();
      },
    });
  }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.service.getAll().subscribe({
      next: (d) => { this.dispatches = d; this.loading = false; this.cdr.markForCheck(); },
      error: () => { this.loading = false; this.cdr.markForCheck(); },
    });
  }

  toggle(dispatch: DamageDispatchResponse): void {
    if (this.expandedId === dispatch.id) {
      this.expandedId = null;
      this.cdr.markForCheck();
      return;
    }
    this.expandedId = dispatch.id;
    if (this.detailCache.has(dispatch.id)) {
      this.cdr.markForCheck();
      return;
    }
    this.loadingDetail = dispatch.id;
    this.cdr.markForCheck();
    this.service.getById(dispatch.id).subscribe({
      next: (d) => {
        this.detailCache.set(d.id, d);
        this.loadingDetail = null;
        this.cdr.markForCheck();
      },
      error: () => { this.loadingDetail = null; this.cdr.markForCheck(); },
    });
  }

  detailFor(id: number): DamageDispatchResponse | undefined {
    return this.detailCache.get(id);
  }
}
