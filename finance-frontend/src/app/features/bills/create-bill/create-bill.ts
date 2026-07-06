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
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { Worker, WorkerResponse } from '../../../core/services/worker';
import { Router } from '@angular/router';
import { Bill } from '../../../core/services/bill';
import { Auth } from '../../../core/services/auth';
import { CustomerService } from '../../../core/services/customer';
import { localDateStr } from '../../../core/utils/date-utils';

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
    MatIconModule,
    MatAutocompleteModule,
    CommonModule],
  templateUrl: './create-bill.html',
  styleUrl: './create-bill.scss',
})
export class CreateBill implements OnInit{
  form: FormGroup;
  loading = false;
  errorMsg = '';
  workers: WorkerResponse[] = [];
  allCustomers: { id: number; name: string; area?: string }[] = [];
  filteredCustomers: { id: number; name: string; area?: string }[] = [];
  selectedCustomerId: number | null = null;



  businesses  = ['RAINCO', 'RETAIL_SHOP', 'PLASTIC', 'HARDWARE', 'STATIONERY'];
  divisions   = ['STORE', 'SHOP'];
  billTypes   = ['CASH', 'CREDIT'];
  billSources = ['SYSTEM', 'MANUAL', 'DRAFT'];

  get isDraft(): boolean {
    return this.form.get('billSource')?.value === 'DRAFT';
  }

  get isEditing(): boolean {
    return !!history.state?.editingBill;
  }

  get editingBill(): any {
    return history.state?.editingBill;
  }

  constructor(
    private fb: FormBuilder,
    private billService: Bill,
    private workerService: Worker,
    private customerService: CustomerService,
    private router: Router,
    private auth: Auth
  ) {
    this.form = this.fb.group({
      business:     ['RAINCO', Validators.required],
      division:     [null, Validators.required],
      billType:     [null, Validators.required],
      billSource:   ['SYSTEM', Validators.required],
      billNumber:   [''],
      area: [null],
      customerName: ['', Validators.required],
      totalAmount:  [null, [Validators.required, Validators.min(0.01)]],
      billDate:     [new Date()],
      workerId:     [null],
      notes:        [''],
    });
  }

  get userDivision(): string {
    const role = this.auth.getRole();
    if (role == 'ACCOUNTANT') return 'STORE';
    if (role == 'SHOP_ACCOUNTANT') return 'SHOP';
    return '';
  }

  get isAdmin(): boolean {
    return this.auth.getRole() === 'ADMIN';
  }


  filterCustomers(value: string): void {
    const q = (value ?? '').toLowerCase();
    this.filteredCustomers = q.length === 0
      ? this.allCustomers.slice(0, 50)
      : this.allCustomers.filter(c => c.name.toLowerCase().includes(q)).slice(0, 50);
  }

  onCustomerSelected(name: string): void {
    const match = this.allCustomers.find(c => c.name === name);
    this.selectedCustomerId = match?.id ?? null;
    // Auto-fill area from customer profile if available
    if (match?.area) {
      this.form.get('area')?.setValue(match.area);
    }
  }

  onCustomerInput(): void {
    this.selectedCustomerId = null;
    this.form.get('area')?.setValue(null);
  }

  onCustomerBlur(): void {
    // If text was typed but no option was selected from the dropdown, reject it
    const val = (this.form.get('customerName')?.value ?? '').trim();
    if (val && !this.selectedCustomerId) {
      this.form.get('customerName')?.setValue('');
      this.form.get('area')?.setValue(null);
      this.form.get('customerName')?.markAsTouched();
    }
  }

  ngOnInit(): void {
    this.loadWorkers();
    this.loadCustomers();

    if (this.isEditing) {
      const b = this.editingBill;
      this.form.patchValue({
        business:     b.business,
        division:     b.division,
        billType:     b.billType,
        billSource:   b.billSource,
        customerName: b.customerName,
        totalAmount:  b.totalAmount,
        area:         b.area,
        billDate:     b.billDate ? new Date(b.billDate) : null,
        workerId:     b.workerId ?? null,
        notes:        b.notes ?? '',
      });
      this.form.get('billSource')?.disable();
      this.form.get('billNumber')?.clearValidators();
      this.form.get('billNumber')?.updateValueAndValidity();
    } else {
      this.watchBillSource();
      if (!this.isAdmin) {
        this.form.get('division')?.setValue(this.userDivision);
        this.form.get('division')?.disable();
      }
    }
  }

  private loadWorkers(): void {
    this.workerService.getAllWorkers().subscribe({
      next: (w) => this.workers = w.filter(w => w.active),
      error: () => this.workers = []
    });
  }

  private loadCustomers(): void {
    this.customerService.getActive().subscribe({
      next: (list) => {
        this.allCustomers = list.map(c => ({ id: c.id, name: c.name, area: c.area }));
        this.filteredCustomers = this.allCustomers.slice(0, 50);
        const ctrl = this.form.get('customerName');
        ctrl?.valueChanges.subscribe(v => this.filterCustomers(v ?? ''));
      },
      error: () => {}
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
    if (!this.selectedCustomerId) {
      this.form.get('customerName')?.setErrors({ mustSelect: true });
      this.form.get('customerName')?.markAsTouched();
      return;
    }

    this.loading = true;
    this.errorMsg = '';

    const raw = this.form.getRawValue();
    const payload = {
      ...raw,
      billDate: raw.billDate ? localDateStr(new Date(raw.billDate)) : localDateStr(),
      customerId: this.selectedCustomerId,
    };
    if (!payload.workerId) delete payload.workerId;
    if (!payload.customerId) delete payload.customerId;

    if (this.isEditing) {
      this.billService.updateBill(this.editingBill.id, payload).subscribe({
        next: () => this.router.navigate(['/bills', this.editingBill.id]),
        error: () => {
          this.errorMsg = 'Failed to update bill. Please try again.';
          this.loading = false;
        }
      });
    } else {
      if (this.isDraft) delete payload.billNumber;
      this.billService.createBill(payload).subscribe({
        next: () => this.router.navigate(['/bills']),
        error: (err) => {
          const msg: string = err?.error?.message ?? err?.message ?? '';
          if (msg.toLowerCase().includes('already exists')) {
            this.form.get('billNumber')?.setErrors({ duplicate: true });
            this.form.get('billNumber')?.markAsTouched();
            this.errorMsg = msg;
          } else {
            this.errorMsg = 'Failed to create bill. Please try again.';
          }
          this.loading = false;
        }
      });
    }
  }

  cancel(): void {
    this.router.navigate(['/bills']);
  }

}
