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

  get availableBillSources(): string[] {
    const business = this.form.get('business')?.value;
    if (business === 'RAINCO') {
      return ['SYSTEM', 'MANUAL', 'MANUAL_BOOK', 'DRAFT'];
    }
    if (business === 'PLASTIC' || business === 'STATIONERY') {
      return ['SYSTEM', 'MANUAL_BOOK', 'DRAFT'];
    }
    return ['SYSTEM', 'MANUAL', 'DRAFT'];
  }

  suggestedBillNumbers: number[] = [];
  loadingNumbers = false;
  readonly String = String;

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
      business:           ['RAINCO', Validators.required],
      division:           [null, Validators.required],
      billType:           [null, Validators.required],
      billSource:         ['SYSTEM', Validators.required],
      billNumber:         [''],
      area:               [null],
      customerName:       ['', Validators.required],
      totalAmount:        [null, [Validators.required, Validators.min(0.01)]],
      billDate:           [new Date()],
      workerId:           [null],
      notes:              [''],
      skippedBillNumbers: [''],
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
    // Delay so optionSelected fires first — clicking a dropdown item blurs the
    // input before the selection event, making selectedCustomerId still null at blur time.
    setTimeout(() => {
      const val = (this.form.get('customerName')?.value ?? '').trim();
      if (val && !this.selectedCustomerId) {
        this.form.get('customerName')?.setValue('');
        this.form.get('area')?.setValue(null);
        this.form.get('customerName')?.markAsTouched();
      }
    }, 200);
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

        // Restore selectedCustomerId when editing — the form patches customerName
        // text but has no customerId field, so we match by name after the list loads.
        if (this.isEditing && !this.selectedCustomerId) {
          const match = this.allCustomers.find(c => c.name === this.editingBill?.customerName);
          if (match) this.selectedCustomerId = match.id;
        }

        const ctrl = this.form.get('customerName');
        ctrl?.valueChanges.subscribe(v => this.filterCustomers(v ?? ''));
      },
      error: () => {}
    });
  }

  private watchBillSource(): void {
    const loadNumbers = () => {
      const business = this.form.get('business')?.value;
      const billSource = this.form.get('billSource')?.value;
      const billNumberControl = this.form.get('billNumber');

      if (billSource === 'DRAFT') {
        billNumberControl?.clearValidators();
        billNumberControl?.setValue('');
        billNumberControl?.updateValueAndValidity();
        this.suggestedBillNumbers = [];
        return;
      }

      billNumberControl?.setValidators(Validators.required);
      billNumberControl?.updateValueAndValidity();

      if (business && billSource && billSource !== 'DRAFT') {
        this.loadingNumbers = true;
        this.billService.getNextBillNumbers(business, billSource).subscribe({
          next: (nums) => {
            this.suggestedBillNumbers = nums;
            if (nums.length > 0 && !this.form.get('billNumber')?.value) {
              this.form.get('billNumber')?.setValue(String(nums[0]));
            }
            this.loadingNumbers = false;
          },
          error: () => { this.loadingNumbers = false; }
        });
      }
    };

    this.form.get('billSource')?.valueChanges.subscribe(loadNumbers);
    this.form.get('business')?.valueChanges.subscribe(() => {
      const currentSource = this.form.get('billSource')?.value;
      const available = this.availableBillSources;
      if (!available.includes(currentSource)) {
        this.form.get('billSource')?.setValue('SYSTEM');
      }
      loadNumbers();
    });

    // Load on init with default values
    loadNumbers();
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
    const skippedRaw: string = raw.skippedBillNumbers ?? '';
    const skippedBillNumbers = skippedRaw
      .split(',')
      .map((s: string) => s.trim())
      .filter((s: string) => s.length > 0);

    const payload: any = {
      ...raw,
      billDate: raw.billDate ? localDateStr(new Date(raw.billDate)) : localDateStr(),
      customerId: this.selectedCustomerId,
      skippedBillNumbers: skippedBillNumbers.length > 0 ? skippedBillNumbers : undefined,
    };
    delete payload.skippedBillNumbers; // remove string field
    if (skippedBillNumbers.length > 0) payload.skippedBillNumbers = skippedBillNumbers;
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
