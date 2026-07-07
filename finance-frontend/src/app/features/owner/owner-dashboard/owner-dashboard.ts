import { CommonModule, DecimalPipe, LowerCasePipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { localDateStr } from '../../../core/utils/date-utils';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { Dashboard, OwnerDashboardData } from '../../../core/services/dashboard';
import { Auth } from '../../../core/services/auth';
import { BillChecklistService, ChecklistVerifyRow } from '../../../core/services/bill-checklist';
import { Payment } from '../../../core/services/payment';
import { MatDialog } from '@angular/material/dialog';
import { ConfirmDialog } from '../../../shared/confirm-dialog/confirm-dialog';
import { from } from 'rxjs';
import { concatMap } from 'rxjs/operators';
import { SelectionModel } from '@angular/cdk/collections';
import { CdkTableModule } from '@angular/cdk/table';
import { RouterLink } from '@angular/router';
import { MatCheckboxModule } from '@angular/material/checkbox';

@Component({
  selector: 'app-owner-dashboard',
  imports: [
    CommonModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatFormFieldModule,
    FormsModule,
    DecimalPipe,
    LowerCasePipe,
    MatCheckboxModule,
    CdkTableModule,
    RouterLink,
  ],
  templateUrl: './owner-dashboard.html',
  styleUrl: './owner-dashboard.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OwnerDashboard implements OnInit {
  data: OwnerDashboardData | null = null;
  loading = true;
  error = false;

  checklistRows: ChecklistVerifyRow[] = [];
  checklistLoading = false;

  selectedBusiness = 'RAINCO';

  get businesses(): string[] {
    return this.auth.isDemo
      ? ['DEMO']
      : ['RAINCO', 'RETAIL_SHOP', 'PLASTIC', 'HARDWARE', 'STATIONERY'];
  }

  get isDemo(): boolean { return this.auth.isDemo; }

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
    private dialog: MatDialog,
    private checklistService: BillChecklistService,
  ) {}

  ngOnInit(): void {
    if (this.auth.isDemo) this.selectedBusiness = 'DEMO';
    this.load();
    this.loadChecklist();
  }

  loadChecklist(): void {
    this.checklistLoading = true;
    const today = localDateStr();
    this.checklistService.getVerifyView(today).subscribe({
      next: (rows) => {
        this.checklistRows = rows;
        this.checklistLoading = false;
        this.cdr.detectChanges();
      },
      error: () => { this.checklistLoading = false; this.cdr.detectChanges(); },
    });
  }

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
    const payment = this.data?.pendingPayments.find(p => p.id === id);
    const lines = [
      `Bill: ${payment?.billNumber ?? ''}`,
      `Customer: ${payment?.customerName ?? ''}`,
      `Amount: Rs ${payment?.amount?.toLocaleString() ?? ''}`,
      `Type: ${payment?.paymentType ?? ''}`,
      `Date: ${payment?.paymentDate ?? ''}`,
      `Entered by: ${payment?.enteredByName ?? ''}`,
      ...(payment?.collectedByOwnerName ? ['Collected by: You (owner)'] : []),
      ...(payment?.collectedByWorkerName ? [`Collected by: ${payment.collectedByWorkerName}`] : []),
      ...(payment?.collectorNote ? [`Note: ${payment.collectorNote}`] : []),
    ].join('\n');

    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Confirm Payment',
        message: lines,
        confirmText: 'Confirm',
        confirmColor: 'primary'
      },
      width: '400px'
    }).afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.paymentService.confirmPayment(id).subscribe({
        next: () => this.load(),
        error: () => alert('Failed to confirm payment.')
      });
    });
  }

  isAllSelected(): boolean {
    return this.selection.selected.length === (this.data?.pendingPayments ?? []).length
      && (this.data?.pendingPayments ?? []).length > 0;
  }

  toggleAll(): void {
    this.isAllSelected()
      ? this.selection.clear()
      : this.data?.pendingPayments.forEach(p => this.selection.select(p));
  }

  confirmSelected(): void {
    if (this.selection.selected.length === 0) return;

    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Confirm Selected Payments',
        message: `Confirm ${this.selection.selected.length} selected payment(s)?`,
        confirmText: 'Confirm All',
        confirmColor: 'primary'
      },
      width: '360px'
    }).afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      const selected = [...this.selection.selected];
      from(selected).pipe(
        concatMap(p => this.paymentService.confirmPayment(p.id))
      ).subscribe({
        error: (err) => alert(err?.error?.message ?? 'Failed to confirm a payment.'),
        complete: () => { this.selection.clear(); this.load(); },
      });
    });
  }
}