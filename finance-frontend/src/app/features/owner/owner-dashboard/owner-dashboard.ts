import { CommonModule, DecimalPipe, LowerCasePipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { Dashboard, OwnerDashboardData } from '../../../core/services/dashboard';
import { Auth } from '../../../core/services/auth';
import { Payment } from '../../../core/services/payment';
import { MatDialog } from '@angular/material/dialog';
import { ConfirmDialog } from '../../../shared/confirm-dialog/confirm-dialog';
import { SelectionModel } from '@angular/cdk/collections';
import { CdkTableModule } from '@angular/cdk/table';
import { MatCheckboxModule } from '@angular/material/checkbox';

@Component({
  selector: 'app-owner-dashboard',
  imports: [  CommonModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatSelectModule,
    MatFormFieldModule,
    FormsModule,
    DecimalPipe,
    LowerCasePipe,
    MatCheckboxModule,
  CdkTableModule],
  templateUrl: './owner-dashboard.html',
  styleUrl: './owner-dashboard.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OwnerDashboard implements OnInit{
  data: OwnerDashboardData | null = null;
  loading = true;
  error = false;

  selectedBusiness = 'RAINCO';
  businesses = ['RAINCO', 'RETAIL_SHOP', 'PLASTIC', 'HARDWARE', 'STATIONERY'];

  paymentColumns   = ['select', 'billNumber', 'customerName', 'amount', 'paymentType', 'enteredByName', 'paymentDate', 'action'];
  unassignedColumns = ['billNumber', 'customerName', 'totalAmount', 'status'];

  selection = new SelectionModel<any>(true, []);



  get greeting(): string {
    const hour = new Date().getHours();
    if (hour < 12) return 'Good morning';
    if (hour < 17) return 'Good afternoon';
    return 'Good evening';
  }

  constructor(
    private dashboardService: Dashboard,
    public auth: Auth,
    private cdr: ChangeDetectorRef,
    private paymentService: Payment,
    private dialog: MatDialog
  ) {}

  ngOnInit(): void { this.load(); }

  onBusinessChange(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.error = false;
    this.selection.clear();
    this.cdr.detectChanges();
    this.dashboardService.getOwnerDashboard(this.selectedBusiness).subscribe({
      next: (d) => {
        this.data = d;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.error = true;
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  confirmPayment(id: number): void {
    const ref = this.dialog.open(ConfirmDialog, {
       data: {
      title:   'Confirm Payment',
      message: 'Are you sure you want to confirm this payment?',
      confirmText: 'Confirm',
      confirmColor: 'primary'
    },
    width: '360px'
  }).afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
        this.paymentService.confirmPayment(id).subscribe({
        next: () => this.load(),
        error: () => alert('Failed to confirm payment.')    
      });
    })  
  }

  isAllSelected(): boolean {
    return  this.selection.selected.length === (this.data?.pendingPayments ?? []).length && (this.data?.pendingPayments ?? []).length > 0;
  }

  toggleAll(): void {
    this.isAllSelected()
    ? this.selection.clear()
    : this.data?.pendingPayments.forEach(p => this.selection.select(p));
  }

  confirmSelected(): void {
    if (this.selection.selected.length === 0) return;

    const ref = this.dialog.open(ConfirmDialog, {
       data: {
      title:       'Confirm Selected Payments',
      message:     `Confirm ${this.selection.selected.length} selected payment(s)?`,
      confirmText: 'Confirm All',
      confirmColor: 'primary'
    },
    width: '360px'
  }).afterClosed().subscribe(confirmed => {
     if (!confirmed) return;
      Promise.all(
        this.selection.selected.map(p =>
          this.paymentService.confirmPayment(p.id).toPromise()
        )
      ).then(() => {
        this.selection.clear();
        this.load();
      });
    });
  }
}
