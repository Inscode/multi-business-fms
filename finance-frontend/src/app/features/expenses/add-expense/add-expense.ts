import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { ExpenseService } from '../../../core/services/expense';
import { Worker, WorkerResponse } from '../../../core/services/worker';

@Component({
  selector: 'app-add-expense',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatProgressSpinnerModule,
    MatIconModule,
  ],
  templateUrl: './add-expense.html',
  styleUrl: './add-expense.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddExpense implements OnInit {
  form: FormGroup;
  submitting = false;
  errorMsg = '';
  workers: WorkerResponse[] = [];

  businesses = ['RAINCO', 'RETAIL_SHOP', 'PLASTIC', 'HARDWARE', 'STATIONERY'];
  categories = [
    { value: 'FUEL',       label: 'Fuel',        hint: 'Vehicle fuel for delivery' },
    { value: 'TEA',        label: 'Tea',          hint: 'Morning / evening tea' },
    { value: 'PARKING',    label: 'Parking',      hint: 'Parking fees' },
    { value: 'REPAIR',     label: 'Repair',       hint: 'Vehicle or equipment repair — needs review' },
    { value: 'PETTY_CASH',    label: 'Petty Cash',     hint: 'Small in-office expenses' },
    { value: 'DRIVER_SALARY', label: 'Driver Salary',  hint: 'Temporary driver salary — needs review if over Rs 3,000' },
    { value: 'OTHER',         label: 'Other',          hint: 'Unplanned expense — needs review' },
  ];

  get needsReview(): boolean {
    const cat = this.form.get('category')?.value;
    const amt = Number(this.form.get('amount')?.value ?? 0);
    if (cat === 'DRIVER_SALARY') return amt > 3000;
    return cat === 'REPAIR' || cat === 'OTHER';
  }

  constructor(
    private fb: FormBuilder,
    private expenseService: ExpenseService,
    private workerService: Worker,
    private router: Router,
    private cdr: ChangeDetectorRef,
  ) {
    this.form = this.fb.group({
      business:    ['RAINCO', Validators.required],
      category:    ['', Validators.required],
      amount:      ['', [Validators.required, Validators.min(1)]],
      date:        [new Date(), Validators.required],
      description: [''],
      workerId:    [null],
    });
  }

  ngOnInit(): void {
    this.workerService.getAllWorkers().subscribe({
      next: (w: WorkerResponse[]) => {
        this.workers = w.filter(w => w.active);
        this.cdr.detectChanges();
      },
      error: () => {},
    });
  }

  submit(): void {
    if (this.form.invalid) return;
    this.submitting = true;
    this.errorMsg = '';

    const v = this.form.getRawValue();
    const date: Date = v.date;

    this.expenseService.create({
      business:    v.business,
      category:    v.category,
      amount:      v.amount,
      date:        `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`,
      description: v.description || undefined,
      workerId:    v.workerId || undefined,
    }).subscribe({
      next: () => {
        this.submitting = false;
        this.router.navigate(['/expenses']);
      },
      error: (err) => {
        this.submitting = false;
        this.errorMsg = err?.error?.message ?? 'Failed to save expense.';
        this.cdr.detectChanges();
      },
    });
  }
}