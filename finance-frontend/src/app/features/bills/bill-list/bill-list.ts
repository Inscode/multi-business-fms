import { CommonModule, DecimalPipe, LowerCasePipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { Worker, WorkerResponse } from '../../../core/services/worker';
import { Bill } from '../../../core/services/bill';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

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
    DecimalPipe,
    LowerCasePipe
  ],
  templateUrl: './bill-list.html',
  styleUrl: './bill-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BillList implements OnInit{
  bills: any[]         = [];
  filteredBills: any[] = [];
  workers: WorkerResponse[] = [];
  loading = true;
  error   = false;


  selectedBusiness = '';
  selectedStatus   = '';

  businesses = ['', 'RAINCO', 'RETAIL_SHOP', 'WHOLESALE', 'HARDWARE', 'STATIONERY'];
  statuses   = ['', 'CREATED', 'ASSIGNED', 'SHOP_WORKER_ASSIGNED',
                'SHOP_RECEIVED', 'STORE_RECEIVED', 'COMPLETED', 'CANCELLED'];

  displayedColumns = ['billNumber', 'customerName', 'business',
                      'totalAmount','balanceRemaining', 'workerName', 'status', 'actions'];

  constructor(
    private billService: Bill,
    private workerService: Worker,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.load();
    this.loadWorkers();
  }

    load(): void {
    this.loading = true;
    this.cdr.detectChanges();
    this.error   = false;
    this.billService.getBills({
      business: this.selectedBusiness || undefined,
      status:   this.selectedStatus   || undefined,
    }).subscribe({
      next: (b) => {
        this.bills         = b;
        this.filteredBills = b;
        this.loading       = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.error   = true;
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

    private loadWorkers(): void {
    this.workerService.getAllWorkers().subscribe({
      next: (w) => this.workers = w.filter(w => w.active),
      error: () => this.workers = []
    });
  }


  onFilterChange(): void {
    this.load();
  }

  assignBill(billId: number, workerId: number): void {
    this.billService.assignBill(billId, workerId).subscribe({
      next: () => this.load(),
      error: () => alert('Failed to assign bill.')
    });
  }

  markReceived(billId: number): void {
    this.billService.markReceived(billId).subscribe({
      next: () => this.load(),
      error: () => alert('Failed to mark as received.')
    });
  }

  canAssign(bill: any): boolean {
  return ['CREATED', 'ASSIGNED', 'STORE_RECEIVED'].includes(bill.status) &&
         !bill.fullyPaid;
  }

  canMarkStoreReceived(bill: any): boolean {
    return ['ASSIGNED', 'SHOP_RECEIVED'].includes(bill.status);
  }

  hasActions(bill: any): boolean {
    return this.canAssign(bill) || this.canMarkStoreReceived(bill);
  }





}
