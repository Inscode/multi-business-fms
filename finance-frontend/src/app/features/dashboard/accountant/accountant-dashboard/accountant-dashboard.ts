import { CommonModule, DatePipe, DecimalPipe, LowerCasePipe } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { AccountantDashboardData, Dashboard } from '../../../../core/services/dashboard';
import { BillReminderResponse, BillReminderService } from '../../../../core/services/bill-reminder';
import { Auth } from '../../../../core/services/auth';

@Component({
  selector: 'app-accountant-dashboard',
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    DecimalPipe,
    LowerCasePipe
  ],
  templateUrl: './accountant-dashboard.html',
  styleUrl: './accountant-dashboard.scss',
})
export class AccountantDashboard implements OnInit {
  data: AccountantDashboardData | null = null;
  loading = true;
  error = false;

  recentColumns    = ['billNumber', 'customerName', 'totalAmount', 'workerName', 'status'];
  unassignedColumns = ['billNumber', 'customerName', 'totalAmount', 'status'];

  get greeting(): string {
    const hour = new Date().getHours();
    if (hour < 12) return 'Good morning';
    if (hour < 17) return 'Good afternoon';
    return 'Good evening';
  }

  get reminders(): BillReminderResponse[] {
    return this.data?.todayReminders ?? [];
  }

  get overdueReminders(): BillReminderResponse[] {
    const today = new Date().toISOString().split('T')[0];
    return this.reminders.filter(r => r.reminderDate < today);
  }

  get todayReminders(): BillReminderResponse[] {
    const today = new Date().toISOString().split('T')[0];
    return this.reminders.filter(r => r.reminderDate === today);
  }

  constructor(
    private dashboardService: Dashboard,
    private reminderService: BillReminderService,
    public auth: Auth,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error   = false;
    this.dashboardService.getAccountantDashboard().subscribe({
      next: (d) => {
        this.data    = d;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.error   = true;
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  markDone(id: number): void {
    this.reminderService.markDone(id).subscribe({
      next: () => this.load(),
      error: () => {},
    });
  }

  cancelReminder(id: number): void {
    this.reminderService.cancel(id).subscribe({
      next: () => this.load(),
      error: () => {},
    });
  }
}