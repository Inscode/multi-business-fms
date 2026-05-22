import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Bill, BillResponse } from '../../../../core/services/bill';
import { CollectionNoteService } from '../../../../core/services/collection-note';

@Component({
  selector: 'app-owner-collect',
  templateUrl: './owner-collect.html',
  styleUrl: './owner-collect.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    DecimalPipe,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
})
export class OwnerCollect implements OnInit {
  bills: BillResponse[] = [];
  filteredBills: BillResponse[] = [];
  searchQuery = '';

  selectedBill: BillResponse | null = null;
  amount: number | null = null;
  paymentType: 'CASH' | 'CHEQUE' = 'CASH';
  notes = '';

  loading = true;
  submitting = false;
  successMsg = '';
  errorMsg = '';

  constructor(
    private billService: Bill,
    private collectionService: CollectionNoteService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.loadBills();
  }

  loadBills(): void {
    this.loading = true;
    this.billService.getBills({}).subscribe({
      next: (bills) => {
        this.bills = bills.filter(
          b => !b.fullyPaid && b.status !== 'CANCELLED' && b.status !== 'COMPLETED'
        ) as BillResponse[];
        this.applySearch();
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  applySearch(): void {
    const q = this.searchQuery.toLowerCase().trim();
    this.filteredBills = q
      ? this.bills.filter(b =>
          b.customerName.toLowerCase().includes(q) ||
          (b.billNumber ?? '').toLowerCase().includes(q)
        )
      : this.bills;
    this.cdr.detectChanges();
  }

  selectBill(bill: BillResponse): void {
    this.selectedBill = bill;
    this.amount = bill.balanceRemaining;
    this.paymentType = 'CASH';
    this.successMsg = '';
    this.errorMsg = '';
    this.cdr.detectChanges();
  }

  clearSelection(): void {
    this.selectedBill = null;
    this.amount = null;
    this.notes = '';
    this.cdr.detectChanges();
  }

  setType(type: 'CASH' | 'CHEQUE'): void {
    this.paymentType = type;
    this.cdr.detectChanges();
  }

  submit(): void {
    if (!this.selectedBill || !this.amount || this.amount <= 0) return;

    this.submitting = true;
    this.errorMsg = '';
    this.successMsg = '';
    this.cdr.detectChanges();

    this.collectionService.create({
      billId: this.selectedBill.id,
      amount: this.amount,
      paymentType: this.paymentType,
      notes: this.notes || undefined,
    }).subscribe({
      next: () => {
        this.successMsg = `Marked Rs ${this.amount?.toLocaleString()} ${this.paymentType} collected from ${this.selectedBill?.customerName}`;
        this.selectedBill = null;
        this.amount = null;
        this.notes = '';
        this.submitting = false;
        this.cdr.detectChanges();
      },
      error: (e) => {
        this.errorMsg = e?.error?.message ?? 'Failed to save. Please try again.';
        this.submitting = false;
        this.cdr.detectChanges();
      },
    });
  }
}