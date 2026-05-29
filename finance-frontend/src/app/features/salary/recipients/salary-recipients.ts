import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { SalaryRecipientResponse, SalaryService } from '../../../core/services/salary';
import { Worker, WorkerResponse } from '../../../core/services/worker';
import { UserService, UserResponse } from '../../../core/services/user';

interface EditState {
  name: string;
  designation: string;
  monthlySalary: number;
  linkedWorkerId?: number;
  linkedUserId?: number;
}

@Component({
  selector: 'app-salary-recipients',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    DecimalPipe,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './salary-recipients.html',
  styleUrl: './salary-recipients.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SalaryRecipientsPage implements OnInit {
  recipients: SalaryRecipientResponse[] = [];
  workers: WorkerResponse[] = [];
  users: UserResponse[] = [];
  loading = true;

  designations = ['WORKER', 'ACCOUNTANT', 'MAIN_ACCOUNTANT', 'DRIVER', 'OTHER'];

  showAddForm = false;
  adding = false;
  newEntry: EditState = { name: '', designation: '', monthlySalary: 0 };

  editingId: number | null = null;
  editState: EditState = { name: '', designation: '', monthlySalary: 0 };

  constructor(
    private salaryService: SalaryService,
    private workerService: Worker,
    private userService: UserService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.load();
    this.workerService.getAllWorkers().subscribe({ next: (w) => { this.workers = w.filter(x => x.active); this.cdr.detectChanges(); }, error: () => {} });
    this.userService.getAll().subscribe({ next: (u) => { this.users = u; this.cdr.detectChanges(); }, error: () => {} });
  }

  load(): void {
    this.loading = true;
    this.salaryService.getAllRecipients().subscribe({
      next: (r) => { this.recipients = r; this.loading = false; this.cdr.detectChanges(); },
      error: () => { this.loading = false; this.cdr.detectChanges(); },
    });
  }

  saveAdd(): void {
    if (!this.newEntry.name || !this.newEntry.designation || this.newEntry.monthlySalary <= 0) return;
    this.adding = true;
    this.salaryService.createRecipient(this.newEntry).subscribe({
      next: () => { this.adding = false; this.showAddForm = false; this.load(); },
      error: () => { this.adding = false; this.cdr.detectChanges(); },
    });
  }

  startEdit(r: SalaryRecipientResponse): void {
    this.editingId = r.id;
    this.editState = { name: r.name, designation: r.designation, monthlySalary: r.monthlySalary ?? 0 };
    this.cdr.detectChanges();
  }

  saveEdit(id: number): void {
    this.salaryService.updateRecipient(id, this.editState).subscribe({
      next: () => { this.editingId = null; this.load(); },
      error: () => alert('Failed to update.'),
    });
  }

  toggle(r: SalaryRecipientResponse): void {
    const action = r.active ? this.salaryService.deactivate(r.id) : this.salaryService.activate(r.id);
    action.subscribe({ next: () => this.load(), error: () => alert('Failed to update status.') });
  }

  designationLabel(d: string): string {
    const map: Record<string,string> = { WORKER: 'Worker', ACCOUNTANT: 'Accountant', MAIN_ACCOUNTANT: 'Main Accountant', DRIVER: 'Driver', OTHER: 'Other' };
    return map[d] ?? d;
  }
}