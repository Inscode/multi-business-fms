import { Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { StockService, StockReductionStatus } from '../../../core/services/stock';

@Component({
  selector: 'app-stock-reduction-status',
  standalone: true,
  imports: [
    CommonModule, FormsModule, DecimalPipe,
    MatTableModule, MatButtonModule, MatCardModule,
    MatIconModule, MatInputModule, MatFormFieldModule,
    MatSelectModule, MatTooltipModule, MatProgressSpinnerModule,
  ],
  templateUrl: './stock-reduction-status.html',
  styleUrl: './stock-reduction-status.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StockReductionStatusComponent implements OnInit {
  allRecords: StockReductionStatus[] = [];
  filtered: StockReductionStatus[] = [];
  loading = false;

  filterSource = 'ALL';
  filterStatus = 'ALL';
  searchText = '';

  columns = ['billNumber', 'billSource', 'customerName', 'amount', 'billDate', 'reductionStatus', 'summaryId', 'enteredByName'];

  summary = { total: 0, notReduced: 0, summaryPending: 0, summaryApproved: 0, individual: 0 };

  constructor(private stockService: StockService, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.stockService.getStockReductionStatus().subscribe({
      next: records => {
        this.allRecords = records;
        this.computeSummary();
        this.applyFilter();
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: err => {
        console.error(err);
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  private computeSummary(): void {
    this.summary.total = this.allRecords.length;
    this.summary.notReduced = this.allRecords.filter(r => r.reductionStatus === 'NOT_REDUCED').length;
    this.summary.summaryPending = this.allRecords.filter(r => r.reductionStatus === 'SUMMARY_PENDING').length;
    this.summary.summaryApproved = this.allRecords.filter(r => r.reductionStatus === 'SUMMARY_APPROVED').length;
    this.summary.individual = this.allRecords.filter(r => r.reductionStatus === 'INDIVIDUALLY_REDUCED').length;
    (this.summary as any).linked = this.allRecords.filter(r => r.reductionStatus === 'LINKED').length;
  }

  applyFilter(): void {
    this.filtered = this.allRecords.filter(r => {
      const matchSource = this.filterSource === 'ALL' || r.billSource === this.filterSource;
      const matchStatus = this.filterStatus === 'ALL' || r.reductionStatus === this.filterStatus;
      const matchText = !this.searchText ||
        r.billNumber.toLowerCase().includes(this.searchText.toLowerCase()) ||
        r.customerName.toLowerCase().includes(this.searchText.toLowerCase());
      return matchSource && matchStatus && matchText;
    });
    this.cdr.detectChanges();
  }

  statusLabel(s: string): string {
    return ({
      NOT_REDUCED: 'Not Reduced',
      SUMMARY_PENDING: 'Summary Pending',
      SUMMARY_APPROVED: 'Summary Approved',
      INDIVIDUALLY_REDUCED: 'Individual',
      LINKED: 'Linked (covered)',
      WILL_LINK: 'Pending Link',
    } as any)[s] ?? s;
  }
}
