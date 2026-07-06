import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { WorkerPortalService, WorkerReminderOverview } from '../../../core/services/worker-portal';

@Component({
  selector: 'app-worker-reminders-overview',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './worker-reminders-overview.html',
  styleUrl: './worker-reminders-overview.scss',
})
export class WorkerRemindersOverview implements OnInit {
  all: WorkerReminderOverview[] = [];
  filtered: WorkerReminderOverview[] = [];
  areas: string[] = [];

  loading = false;
  error = false;

  searchQuery = '';
  areaFilter = '';

  constructor(
    private svc: WorkerPortalService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.error = false;
    this.cdr.detectChanges();
    this.svc.getRemindersOverview().subscribe({
      next: items => {
        this.all = items;
        this.areas = [...new Set(items.map(r => r.area).filter((a): a is string => !!a))].sort();
        this.applyFilters();
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.error = true;
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  applyFilters(): void {
    const q = this.searchQuery.toLowerCase().trim();
    this.filtered = this.all.filter(r => {
      const matchSearch = !q || r.customerName.toLowerCase().includes(q) || r.billNumber.toLowerCase().includes(q);
      const matchArea = !this.areaFilter || r.area === this.areaFilter;
      return matchSearch && matchArea;
    });
    this.cdr.detectChanges();
  }

  clearSearch(): void {
    this.searchQuery = '';
    this.applyFilters();
  }

  isOverdue(reminderDate: string): boolean {
    return new Date(reminderDate) < new Date(new Date().toDateString());
  }

  periodLabel(p: string): string {
    const map: Record<string, string> = {
      DAILY: 'Daily',
      WEEKLY: 'Weekly',
      MONTHLY: 'Monthly',
      ONCE: 'One-time',
    };
    return map[p] ?? p;
  }
}
