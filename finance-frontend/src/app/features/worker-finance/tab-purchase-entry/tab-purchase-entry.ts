import { Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { localDateStr } from '../../../core/utils/date-utils';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { SalaryRecipientResponse, SalaryService } from '../../../core/services/salary';
import {
  WorkerFinanceService,
  WorkerTabPurchaseItemRequest,
  WorkerTabPurchaseResponse,
} from '../../../core/services/worker-finance';

interface DraftItem {
  description: string;
  quantity: number | null;
  unitPrice: number | null;
}

@Component({
  selector: 'app-tab-purchase-entry',
  standalone: true,
  imports: [
    CommonModule, FormsModule, DecimalPipe,
    MatButtonModule, MatCardModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatIconModule,
    MatProgressSpinnerModule, MatTooltipModule,
  ],
  templateUrl: './tab-purchase-entry.html',
  styleUrl: './tab-purchase-entry.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TabPurchaseEntry implements OnInit {
  recipients: SalaryRecipientResponse[] = [];
  myEntries: WorkerTabPurchaseResponse[] = [];
  expandedId: number | null = null;

  // Bill header
  recipientId: number | null = null;
  billDate = localDateStr();
  notes = '';

  // Line items
  draftItems: DraftItem[] = [{ description: '', quantity: null, unitPrice: null }];

  submitting = false;
  errorMsg = '';
  successMsg = '';

  get billTotal(): number {
    return this.draftItems.reduce((s, i) => s + (i.quantity ?? 0) * (i.unitPrice ?? 0), 0);
  }

  get canSubmit(): boolean {
    return !!this.recipientId && !!this.billDate &&
      this.draftItems.some(i => i.description && i.quantity && i.unitPrice);
  }

  constructor(
    private salaryService: SalaryService,
    private workerFinanceService: WorkerFinanceService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.salaryService.getActiveRecipients().subscribe({
      next: (r) => { this.recipients = r; this.cdr.detectChanges(); },
      error: () => {},
    });
    this.loadMyEntries();
  }

  loadMyEntries(): void {
    this.workerFinanceService.getAllTabPurchases().subscribe({
      next: (list) => { this.myEntries = list.slice(0, 30); this.cdr.detectChanges(); },
      error: () => {},
    });
  }

  addItem(): void {
    this.draftItems.push({ description: '', quantity: null, unitPrice: null });
    this.cdr.detectChanges();
  }

  removeItem(index: number): void {
    if (this.draftItems.length === 1) return;
    this.draftItems.splice(index, 1);
    this.cdr.detectChanges();
  }

  lineTotal(item: DraftItem): number {
    return (item.quantity ?? 0) * (item.unitPrice ?? 0);
  }

  submit(): void {
    if (!this.canSubmit) return;
    const validItems = this.draftItems.filter(i => i.description && i.quantity && i.unitPrice);
    if (validItems.length === 0) return;

    this.submitting = true;
    this.errorMsg = '';
    this.successMsg = '';

    const items: WorkerTabPurchaseItemRequest[] = validItems.map(i => ({
      description: i.description,
      quantity: i.quantity!,
      unitPrice: i.unitPrice!,
    }));

    this.workerFinanceService.createTabPurchase({
      recipientId: this.recipientId!,
      billDate: this.billDate,
      notes: this.notes || undefined,
      items,
    }).subscribe({
      next: () => {
        this.submitting = false;
        this.successMsg = `Purchase bill submitted (${validItems.length} item${validItems.length > 1 ? 's' : ''}) — awaiting owner approval.`;
        this.draftItems = [{ description: '', quantity: null, unitPrice: null }];
        this.notes = '';
        this.loadMyEntries();
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.submitting = false;
        this.errorMsg = err?.error?.message ?? 'Failed to submit.';
        this.cdr.detectChanges();
      },
    });
  }

  resetForm(): void {
    this.recipientId = null;
    this.billDate = localDateStr();
    this.notes = '';
    this.draftItems = [{ description: '', quantity: null, unitPrice: null }];
    this.errorMsg = '';
    this.successMsg = '';
    this.cdr.detectChanges();
  }

  toggleExpand(id: number): void {
    this.expandedId = this.expandedId === id ? null : id;
    this.cdr.detectChanges();
  }

  statusLabel(s: string): string {
    const map: Record<string, string> = {
      PENDING_OWNER: 'Pending Owner', OWNER_APPROVED: 'Owner Approved', PAID: 'Deducted from Salary', REJECTED: 'Rejected',
    };
    return map[s] ?? s;
  }
}
