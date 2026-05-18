import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { Worker, WorkerResponse } from '../../../core/services/worker';
import { Router } from '@angular/router';
import { Bill } from '../../../core/services/bill';

@Component({
  selector: 'app-create-bill',
  imports: [   CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatProgressSpinnerModule,
    MatIconModule,],
  templateUrl: './create-bill.html',
  styleUrl: './create-bill.scss',
})
export class CreateBill implements OnInit{
  form: FormGroup;
  loading = false;
  errorMsg = '';
  workers: WorkerResponse[] = [];



  businesses  = ['RAINCO', 'RETAIL_SHOP', 'WHOLESALE', 'HARDWARE', 'STATIONERY'];
  divisions   = ['STORE', 'SHOP'];
  billTypes   = ['CASH', 'CREDIT'];
  billSources = ['MANUAL', 'SYSTEM', 'DRAFT'];

  get isDraft(): boolean {
    return this.form.get('billSource')?.value === 'DRAFT';
  }

    constructor(
    private fb: FormBuilder,
    private billService: Bill,
    private workerService: Worker,
    private router: Router
  ) {
    this.form = this.fb.group({
      business:     [null, Validators.required],
      division:     [null, Validators.required],
      billType:     [null, Validators.required],
      billSource:   [null, Validators.required],
      billNumber:   [''],
      customerName: ['', Validators.required],
      totalAmount:  [null, [Validators.required, Validators.min(0.01)]],
      billDate:     [new Date()],
      workerId:     [null],
      notes:        [''],
    });
  }


  ngOnInit(): void {
    this.loadWorkers();
    this.watchBillSource();
  }

  private loadWorkers(): void {
    this.workerService.getAllWorkers().subscribe({
      next: (w) => this.workers = w.filter(w => w.active),
      error: () => this.workers = []
    });
  }

  private watchBillSource(): void {
    this.form.get('billSource')?.valueChanges.subscribe(source => {
      const billNumberControl = this.form.get('billNumber');
      if (source === 'DRAFT') {
        billNumberControl?.clearValidators();
        billNumberControl?.setValue('');
      } else {
        billNumberControl?.setValidators(Validators.required);
      }
      billNumberControl?.updateValueAndValidity();
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading = true;
    this.errorMsg = '';

    const payload = { ...this.form.value };
    if (this.isDraft) delete payload.billNumber;
    if (!payload.workerId) delete payload.workerId;

    this.billService.createBill(payload).subscribe({
      next: () => this.router.navigate(['/bills']),
      error: () => {
        this.errorMsg = 'Failed to create bill. Please try again.';
        this.loading = false;
      }
    });
  }

  cancel(): void {
    this.router.navigate(['/bills']);
  }

}
