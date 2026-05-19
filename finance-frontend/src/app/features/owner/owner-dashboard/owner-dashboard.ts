import { CommonModule, DecimalPipe, LowerCasePipe } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { Dashboard, OwnerDashboardData } from '../../../core/services/dashboard';
import { Auth } from '../../../core/services/auth';
import { Payment } from '../../../core/services/payment';

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
    LowerCasePipe,],
  templateUrl: './owner-dashboard.html',
  styleUrl: './owner-dashboard.scss',
})
export class OwnerDashboard implements OnInit{
  data: OwnerDashboardData | null = null;
  loading = true;
  error = false;

   selectedBusiness = 'RAINCO';
  businesses = ['RAINCO', 'RETAIL_SHOP', 'PLASTIC', 'HARDWARE', 'STATIONERY'];

  paymentColumns   = ['billNumber', 'customerName', 'amount', 'paymentType', 'enteredByName', 'paymentDate', 'action'];
  unassignedColumns = ['billNumber', 'customerName', 'totalAmount', 'status'];

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
    private paymentService: Payment
  ) {}

  ngOnInit(): void { this.load(); }

  onBusinessChange(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.error = false;
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
    this.paymentService.confirmPayment(id).subscribe({
      next: () => this.load(),
      error: () => alert('Failed to confirm payment.')
    })
  }


}
