import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, OnInit, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { SelectionModel } from '@angular/cdk/collections';
import { Bill, BillResponse, SkipReviewResponse } from '../../../core/services/bill';
import { ConfirmDialog } from '../../../shared/confirm-dialog/confirm-dialog';

@Component({
  selector: 'app-review-bills-tab',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    DecimalPipe,
    DatePipe,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatCheckboxModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    MatDialogModule,
  ],
  templateUrl: './review-bills-tab.html',
  styleUrl: './review-bills-tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReviewBillsTab implements OnInit {
  @Output() countChanged = new EventEmitter<number>();

  bills: BillResponse[] = [];
  loading = false;
  markingAll = false;
  markingSelected = false;

  pendingSkips: SkipReviewResponse[] = [];
  loadingSkips = false;
  processingSkipId: number | null = null;

  selection = new SelectionModel<BillResponse>(true, []);

  displayedColumns = ['select', 'billNumber', 'customerName', 'area', 'business', 'billType', 'totalAmount', 'enteredBy', 'createdAt'];

  constructor(
    private billService: Bill,
    private cdr: ChangeDetectorRef,
    private dialog: MatDialog,
  ) {}

  ngOnInit(): void {
    this.load();
    this.loadSkips();
  }

  load(): void {
    this.loading = true;
    this.selection.clear();
    this.billService.getUnreviewedBills().subscribe({
      next: (bills) => {
        this.bills = bills;
        this.loading = false;
        this.countChanged.emit(bills.length);
        this.cdr.markForCheck();
      },
      error: () => {
        this.loading = false;
        this.cdr.markForCheck();
      },
    });
  }

  get allSelected(): boolean {
    return this.bills.length > 0 && this.selection.selected.length === this.bills.length;
  }

  get someSelected(): boolean {
    return this.selection.selected.length > 0 && !this.allSelected;
  }

  toggleAll(): void {
    if (this.allSelected) {
      this.selection.clear();
    } else {
      this.bills.forEach(b => this.selection.select(b));
    }
  }

  markSelected(): void {
    const ids = this.selection.selected.map(b => b.id);
    this.markingSelected = true;
    this.billService.markBillsReviewed(ids).subscribe({
      next: () => { this.markingSelected = false; this.load(); },
      error: () => { this.markingSelected = false; this.cdr.markForCheck(); },
    });
  }

  loadSkips(): void {
    this.loadingSkips = true;
    this.billService.getPendingSkips().subscribe({
      next: (skips) => { this.pendingSkips = skips; this.loadingSkips = false; this.cdr.markForCheck(); },
      error: () => { this.loadingSkips = false; this.cdr.markForCheck(); },
    });
  }

  approveSkip(skip: SkipReviewResponse): void {
    this.processingSkipId = skip.id;
    this.cdr.markForCheck();
    this.billService.approveSkip(skip.id).subscribe({
      next: () => { this.processingSkipId = null; this.loadSkips(); },
      error: () => { this.processingSkipId = null; this.cdr.markForCheck(); },
    });
  }

  rejectSkip(skip: SkipReviewResponse): void {
    this.processingSkipId = skip.id;
    this.cdr.markForCheck();
    this.billService.rejectSkip(skip.id).subscribe({
      next: () => { this.processingSkipId = null; this.loadSkips(); },
      error: () => { this.processingSkipId = null; this.cdr.markForCheck(); },
    });
  }

  markAll(): void {
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Mark All as Reviewed',
        message: `Mark all ${this.bills.length} unreviewed bill${this.bills.length !== 1 ? 's' : ''} as reviewed?\n\nNew bills entered after this will appear here as unreviewed.`,
        confirmText: 'Mark All Reviewed',
        confirmColor: 'primary',
      },
      width: '420px',
    }).afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.markingAll = true;
      this.cdr.markForCheck();
      this.billService.markAllBillsReviewed().subscribe({
        next: () => { this.markingAll = false; this.load(); },
        error: () => { this.markingAll = false; this.cdr.markForCheck(); },
      });
    });
  }
}
