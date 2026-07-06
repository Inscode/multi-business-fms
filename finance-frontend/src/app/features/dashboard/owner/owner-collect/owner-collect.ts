import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Auth } from '../../../../core/services/auth';
import { Bill, BillResponse } from '../../../../core/services/bill';
import { CollectionNoteService } from '../../../../core/services/collection-note';

interface CombinedItem {
  bill: BillResponse;
  amount: number;
}

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
    MatTooltipModule,
  ],
})
export class OwnerCollect implements OnInit {
  bills: BillResponse[] = [];
  filteredBills: BillResponse[] = [];
  searchQuery = '';
  selectedBillType: '' | 'CASH' | 'CREDIT' = '';

  // ── Single mode ──
  selectedBill: BillResponse | null = null;
  amount: number | null = null;
  paymentType: 'CASH' | 'CHEQUE' | 'BANK_TRANSFER' = 'CASH';
  notes = '';

  // ── Mode toggle ──
  mode: 'single' | 'combined' = 'single';

  // ── Combined mode ──
  combinedItems: CombinedItem[] = [];
  combinedPaymentType: 'CASH' | 'CHEQUE' | 'BANK_TRANSFER' = 'CASH';
  combinedNotes = '';
  chequeNumber = '';
  bankName = '';
  branchName = '';
  referenceNumber = '';
  submittingCombined = false;

  loading = true;
  submitting = false;
  successMsg = '';
  errorMsg = '';

  get canEditAmount(): boolean {
    const r = this.auth.getRole();
    return r === 'ADMIN' || r === 'OWNER';
  }

  get combinedTotal(): number {
    return this.combinedItems.reduce((s, i) => s + (i.amount || 0), 0);
  }

  get combinedValid(): boolean {
    return this.combinedItems.length >= 2 &&
           this.combinedItems.every(i => i.amount > 0);
  }

  get inCombined(): boolean {
    return this.mode === 'combined';
  }

  constructor(
    private billService: Bill,
    private collectionService: CollectionNoteService,
    private auth: Auth,
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
    this.filteredBills = this.bills.filter(b => {
      const matchesSearch = !q ||
        b.customerName.toLowerCase().includes(q) ||
        (b.billNumber ?? '').toLowerCase().includes(q);
      const matchesType = !this.selectedBillType || b.billType === this.selectedBillType;
      return matchesSearch && matchesType;
    });
    this.cdr.detectChanges();
  }

  setTypeFilter(type: '' | 'CASH' | 'CREDIT'): void {
    this.selectedBillType = type;
    this.applySearch();
  }

  setMode(m: 'single' | 'combined'): void {
    this.mode = m;
    this.selectedBill = null;
    this.combinedItems = [];
    this.successMsg = '';
    this.errorMsg = '';
    this.cdr.detectChanges();
  }

  // ── Single mode ──

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

  setType(type: 'CASH' | 'CHEQUE' | 'BANK_TRANSFER'): void {
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

  // ── Combined mode ──

  isInCart(billId: number): boolean {
    return this.combinedItems.some(i => i.bill.id === billId);
  }

  addToCombined(bill: BillResponse): void {
    if (this.isInCart(bill.id)) return;
    this.combinedItems = [...this.combinedItems, { bill, amount: bill.balanceRemaining }];
    this.cdr.detectChanges();
  }

  removeFromCombined(billId: number): void {
    this.combinedItems = this.combinedItems.filter(i => i.bill.id !== billId);
    this.cdr.detectChanges();
  }

  setCombinedType(type: 'CASH' | 'CHEQUE' | 'BANK_TRANSFER'): void {
    this.combinedPaymentType = type;
    this.cdr.detectChanges();
  }

  submitCombined(): void {
    if (!this.combinedValid) return;

    this.submittingCombined = true;
    this.errorMsg = '';
    this.successMsg = '';
    this.cdr.detectChanges();

    const payload: import('../../../../core/services/collection-note').CollectionNoteBulkPayload = {
      paymentType: this.combinedPaymentType,
      notes: this.combinedNotes || undefined,
      bills: this.combinedItems.map(i => ({ billId: i.bill.id, amount: i.amount })),
    };

    if (this.combinedPaymentType === 'CHEQUE') {
      payload.chequeNumber = this.chequeNumber || undefined;
      payload.bankName = this.bankName || undefined;
      payload.branchName = this.branchName || undefined;
    }

    if (this.combinedPaymentType === 'BANK_TRANSFER') {
      payload.referenceNumber = this.referenceNumber || undefined;
    }

    this.collectionService.createBulk(payload).subscribe({
      next: () => {
        this.successMsg = `Combined collection of Rs ${this.combinedTotal.toLocaleString()} across ${this.combinedItems.length} bills saved.`;
        this.combinedItems = [];
        this.chequeNumber = '';
        this.bankName = '';
        this.branchName = '';
        this.referenceNumber = '';
        this.combinedNotes = '';
        this.submittingCombined = false;
        this.cdr.detectChanges();
      },
      error: (e) => {
        this.errorMsg = e?.error?.message ?? 'Failed to save combined collection.';
        this.submittingCombined = false;
        this.cdr.detectChanges();
      },
    });
  }
}
