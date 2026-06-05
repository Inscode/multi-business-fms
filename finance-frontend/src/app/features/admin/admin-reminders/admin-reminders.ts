import { Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { BillReminderResponse, BillReminderService } from '../../../core/services/bill-reminder';

@Component({
  selector: 'app-admin-reminders',
  standalone: true,
  imports: [
    CommonModule, FormsModule, DecimalPipe,
    MatButtonModule, MatIconModule, MatProgressSpinnerModule,
  ],
  templateUrl: './admin-reminders.html',
  styleUrl: './admin-reminders.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminReminders implements OnInit {
  reminders: BillReminderResponse[] = [];
  loading = false;
  editingId: number | null = null;
  editDate = '';
  editPeriod = 'ANYTIME';
  editNote = '';

  periods = ['MORNING', 'AFTERNOON', 'EVENING', 'ANYTIME'];

  constructor(private service: BillReminderService, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.service.getAllReminders().subscribe({
      next: (r) => { this.reminders = r; this.loading = false; this.cdr.detectChanges(); },
      error: () => { this.loading = false; this.cdr.detectChanges(); },
    });
  }

  startEdit(r: BillReminderResponse): void {
    this.editingId = r.id;
    this.editDate = r.reminderDate;
    this.editPeriod = r.period;
    this.editNote = r.note ?? '';
    this.cdr.detectChanges();
  }

  cancelEdit(): void { this.editingId = null; this.cdr.detectChanges(); }

  saveEdit(): void {
    if (!this.editingId) return;
    this.service.updateReminder(this.editingId, {
      reminderDate: this.editDate,
      period: this.editPeriod,
      note: this.editNote || undefined,
    }).subscribe({
      next: () => { this.editingId = null; this.load(); },
      error: () => alert('Failed to update reminder.'),
    });
  }

  cancel(id: number): void {
    if (!confirm('Cancel this reminder?')) return;
    this.service.cancel(id).subscribe({
      next: () => this.load(),
      error: () => alert('Failed to cancel reminder.'),
    });
  }

  statusClass(s: string): string {
    return s.toLowerCase();
  }
}
