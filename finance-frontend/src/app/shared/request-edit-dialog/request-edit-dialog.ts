import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, Inject, OnInit } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { EditRequestService } from '../../core/services/edit-request';
import { CustomerService, CustomerResponse } from '../../core/services/customer';

export interface RequestEditDialogData {
  type: 'BILL' | 'PAYMENT';
  targetId: number;
  targetRef: string;
  current: Record<string, any>;
}

@Component({
  selector: 'app-request-edit-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatProgressSpinnerModule,
    MatAutocompleteModule,
  ],
  templateUrl: './request-edit-dialog.html',
  styleUrl: './request-edit-dialog.scss',
})
export class RequestEditDialog implements OnInit {
  form!: FormGroup;
  submitting = false;
  errorMsg = '';

  businesses   = ['RAINCO', 'RETAIL_SHOP', 'PLASTIC', 'HARDWARE', 'STATIONERY', 'MIX'];
  billTypes    = ['CASH', 'CREDIT'];
  divisions    = ['STORE', 'SHOP'];
  paymentTypes = ['CASH', 'CHEQUE', 'BANK_TRANSFER'];
  areas = [
    'Badalkumbura', 'Badulla', 'Bandarawela', 'Beragala',
    'Bogakumbura', 'Boralanda', 'Diyatalawa', 'Ella',
    'Etampitiya', 'Haldummulla', 'Hali-Ela', 'Hasalaka', 'Haputale',
    'Kandaketiya', 'Kumbalwela', 'Lunugala', 'Mahiyanganaya',
    'Meegahakivula', 'Passara', 'Uva-Paranagama', 'Welimada',
  ];

  allCustomers: CustomerResponse[] = [];
  filteredCustomers: CustomerResponse[] = [];
  customerSearchCtrl = new FormControl('');

  get isBill(): boolean { return this.data.type === 'BILL'; }

  get isCheque(): boolean {
    return this.form?.get('paymentType')?.value === 'CHEQUE';
  }

  constructor(
    private fb: FormBuilder,
    private editRequestService: EditRequestService,
    private customerService: CustomerService,
    public dialogRef: MatDialogRef<RequestEditDialog>,
    @Inject(MAT_DIALOG_DATA) public data: RequestEditDialogData,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    const c = this.data.current;
    if (this.isBill) {
      this.form = this.fb.group({
        customerName: [c['customerName'] ?? '', Validators.required],
        totalAmount:  [c['totalAmount']  ?? null, [Validators.required, Validators.min(0.01)]],
        business:     [c['business']     ?? null],
        billType:     [c['billType']     ?? null, Validators.required],
        division:     [c['division']     ?? null],
        area:         [c['area']         ?? null],
        billDate:     [c['billDate'] ? new Date(c['billDate']) : null],
        notes:        [c['notes']        ?? ''],
        reason:       ['', Validators.required],
      });

      this.customerSearchCtrl.setValue(c['customerName'] ?? '');

      this.customerService.getActive().subscribe({
        next: (customers) => {
          this.allCustomers = customers;
          this.filterCustomers(this.customerSearchCtrl.value ?? '');
          this.cdr.markForCheck();
        },
        error: () => {},
      });

      // Option value is a string (customer name), so this ctrl always holds a string
      this.customerSearchCtrl.valueChanges.subscribe(val => {
        this.filterCustomers(val ?? '');
      });
    } else {
      this.form = this.fb.group({
        amount:       [c['paymentAmount'] ?? null, [Validators.required, Validators.min(0.01)]],
        paymentType:  [c['paymentType']   ?? null, Validators.required],
        paymentDate:  [c['paymentDate'] ? new Date(c['paymentDate']) : new Date()],
        chequeNumber: [c['chequeNumber']  ?? ''],
        chequeDate:   [c['chequeDate'] ? new Date(c['chequeDate']) : null],
        bankName:     [c['bankName']      ?? ''],
        branchName:   [c['branchName']    ?? ''],
        referenceNumber: [c['referenceNumber'] ?? ''],
        notes:        [c['notes']         ?? ''],
        reason:       ['', Validators.required],
      });
    }
  }

  onCustomerSelected(event: MatAutocompleteSelectedEvent): void {
    // option [value] is c.name (string) — Material writes it directly to the input, no displayWith needed
    const name = event.option.value as string;
    const customer = this.allCustomers.find(c => c.name === name);
    this.form.get('customerName')!.setValue(name);
    if (customer?.area) this.form.get('area')!.setValue(customer.area);
  }

  private filterCustomers(search: string): void {
    const s = search.toLowerCase().trim();
    this.filteredCustomers = s
      ? this.allCustomers.filter(c => c.name.toLowerCase().includes(s) || (c.area ?? '').toLowerCase().includes(s))
      : this.allCustomers.slice(0, 30);
  }

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }

    this.submitting = true;
    this.errorMsg = '';

    const { reason, ...rest } = this.form.value;

    // Dates must go out as plain yyyy-MM-dd — JSON.stringify would turn a Date into
    // a UTC instant, which both displays badly for the admin and can shift the day.
    const changes: Record<string, any> = {};
    for (const [key, value] of Object.entries(rest)) {
      changes[key] = value instanceof Date ? this.toLocalDateString(value) : value;
    }

    this.editRequestService.create({
      type:             this.data.type,
      targetId:         this.data.targetId,
      targetRef:        this.data.targetRef,
      requestedChanges: JSON.stringify(changes),
      reason,
    }).subscribe({
      next: () => this.dialogRef.close(true),
      error: () => {
        this.errorMsg = 'Failed to submit request. Please try again.';
        this.submitting = false;
        this.cdr.markForCheck();
      },
    });
  }

  private toLocalDateString(d: Date): string {
    const month = `${d.getMonth() + 1}`.padStart(2, '0');
    const day = `${d.getDate()}`.padStart(2, '0');
    return `${d.getFullYear()}-${month}-${day}`;
  }
}
