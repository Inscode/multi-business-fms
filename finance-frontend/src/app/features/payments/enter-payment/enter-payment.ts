import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { Payment } from '../../../core/services/payment';
import { Bill } from '../../../core/services/bill';
import { Worker, WorkerResponse } from '../../../core/services/worker';
import { Auth } from '../../../core/services/auth';
import { Router } from '@angular/router';
import { BANKS, AREAS } from '../../../core/constants/payment-options';

@Component({
  selector: 'app-enter-payment',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatProgressSpinnerModule,
    MatIconModule,
  ],
  templateUrl: './enter-payment.html',
  styleUrl: './enter-payment.scss',
})
export class EnterPayment implements OnInit {
  form: FormGroup;
  loading = false;
  errorMsg = '';

  bills: any[] = [];
  selectedBill: any = null;
  filterBusiness = '';

  workers: WorkerResponse[] = [];

  businesses   = ['RAINCO', 'RETAIL_SHOP', 'PLASTIC', 'HARDWARE', 'STATIONERY'];
  paymentTypes = ['CASH', 'CHEQUE', 'BANK_TRANSFER'];
  banks = BANKS;
  areas = AREAS;

  get isCheque(): boolean {
    return this.form.get('paymentType')?.value === 'CHEQUE';
  }

  get isBankTransfer(): boolean {
    return this.form.get('paymentType')?.value === 'BANK_TRANSFER';
  }

  get isEditing(): boolean {
    return !!history.state?.payment;
  }

  get editingPayment(): any {
    return history.state?.payment;
  }

  get collectionNoteId(): number | null {
    return history.state?.collectionNoteId ?? null;
  }

  constructor(
    private fb: FormBuilder,
    private paymentService: Payment,
    private billService: Bill,
    private workerService: Worker,
    private auth: Auth,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {
    this.form = this.fb.group({
      paymentType:          [null, Validators.required],
      amount:               [null, [Validators.required, Validators.min(0.01)]],
      paymentDate:          [new Date()],
      chequeNumber:         [''],
      chequeDate:           [null],
      bankName:             [''],
      branchName:           [''],
      referenceNumber:      [''],
      notes:                [''],
      collectedByWorkerId:  [null],
      collectorNote:        [''],
    });
  }

  ngOnInit(): void {
    this.loadBills();
    this.loadWorkers();
    this.watchPaymentType();

    if (this.isEditing) {
      this.prefillForm(this.editingPayment);
    }

    const preselected = history.state?.preselectedBill;
    if (preselected) {
      this.selectedBill = preselected;
      this.onBillSelect(preselected);
    }

    // Pre-fill from a confirmed worker CollectionNote
    const state = history.state ?? {};
    if (state.prefillAmount)      this.form.patchValue({ amount: state.prefillAmount });
    if (state.prefillPaymentType) this.form.patchValue({ paymentType: state.prefillPaymentType });
    if (state.prefillChequeNumber) this.form.patchValue({ chequeNumber: state.prefillChequeNumber });
    if (state.prefillBankName)    this.form.patchValue({ bankName: state.prefillBankName });
    if (state.prefillBranchName)  this.form.patchValue({ branchName: state.prefillBranchName });
  }

  private loadBills(): void {
    this.billService.getBills({
      business: this.filterBusiness || undefined
    }).subscribe({
      next: (b) => {
        this.bills = b.filter(
          b => !b.fullyPaid &&
               b.status !== 'CANCELLED' &&
               b.status !== 'COMPLETED'
        );
        this.cdr.markForCheck();
      },
      error: () => { this.bills = []; this.cdr.markForCheck(); }
    });
  }

  private loadWorkers(): void {
    this.workerService.getAllWorkers().subscribe({
      next: (w) => { this.workers = w.filter(w => w.active); this.cdr.markForCheck(); },
      error: () => { this.workers = []; this.cdr.markForCheck(); }
    });
  }

  onBusinessFilterChange(): void {
    this.selectedBill = null;
    this.loadBills();
  }

  onBillSelect(bill: any): void {
    this.selectedBill = bill;
    const maxAmount = bill.balanceRemaining;
    this.form.get('amount')?.setValidators([
      Validators.required,
      Validators.min(0.01),
      Validators.max(maxAmount)
    ]);
    this.form.get('amount')?.updateValueAndValidity();
  }

  private watchPaymentType(): void {
    this.form.get('paymentType')?.valueChanges.subscribe(type => {
      this.clearConditionalValidators();

      if (type === 'CHEQUE') {
        this.form.get('chequeNumber')?.setValidators(Validators.required);
        this.form.get('chequeDate')?.setValidators(Validators.required);
        this.form.get('bankName')?.setValidators(Validators.required);
        this.form.get('branchName')?.setValidators(Validators.required);
      } else if (type === 'BANK_TRANSFER') {
        this.form.get('referenceNumber')?.setValidators(Validators.required);
        this.form.get('bankName')?.setValidators(Validators.required);
      }

      this.updateConditionalValidity();
    });
  }

  private clearConditionalValidators(): void {
    ['chequeNumber', 'chequeDate', 'bankName', 'branchName', 'referenceNumber'].forEach(field => {
      this.form.get(field)?.clearValidators();
    });
    this.updateConditionalValidity();
  }

  private updateConditionalValidity(): void {
    ['chequeNumber', 'chequeDate', 'bankName', 'branchName', 'referenceNumber'].forEach(field => {
      this.form.get(field)?.updateValueAndValidity();
    });
  }

  private prefillForm(payment: any): void {
    this.form.patchValue({
      paymentType:         payment.paymentType,
      amount:              payment.paymentAmount,
      paymentDate:         this.parseLocalDate(payment.paymentDate),
      chequeNumber:        payment.chequeNumber,
      chequeDate:          payment.chequeDate ? this.parseLocalDate(payment.chequeDate) : null,
      bankName:            payment.bankName,
      branchName:          payment.branchName,
      referenceNumber:     payment.referenceNumber,
      notes:               payment.notes,
      collectedByWorkerId: payment.collectedByWorkerId ?? null,
      collectorNote:       payment.collectorNote ?? '',
    });
  }

  // Parse a YYYY-MM-DD string in local time to avoid UTC midnight shift
  private parseLocalDate(s: string): Date {
    const [y, m, d] = s.split('-').map(Number);
    return new Date(y, m - 1, d);
  }

  private toLocalDateStr(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    if (!this.selectedBill && !this.isEditing) {
      this.errorMsg = 'Please select a bill.';
      return;
    }

    this.loading  = true;
    this.errorMsg = '';

    const fv = this.form.value;
    const payload = {
      ...fv,
      paymentDate: fv.paymentDate ? this.toLocalDateStr(fv.paymentDate) : null,
      chequeDate:  fv.chequeDate  ? this.toLocalDateStr(fv.chequeDate)  : null,
      ...(this.collectionNoteId ? { collectionNoteId: this.collectionNoteId } : {}),
    };

    const request$ = this.isEditing
      ? this.paymentService.updatePayment(this.editingPayment.id, payload)
      : this.paymentService.enterPayment(this.selectedBill.id, payload);

    request$.subscribe({
      next: () => this.redirect(),
      error: (e) => {
        this.errorMsg = e?.error?.message ?? 'Failed to save payment.';
        this.loading  = false;
        this.cdr.markForCheck();
      }
    });
  }

  cancel(): void { this.redirect(); }

  private redirect(): void {
    if (this.auth.getRole() === 'SHOP_ACCOUNTANT') {
      this.router.navigate(['/dashboard/shop']);
      return;
    }
    // Return to the bill detail if we came from one
    const billId = this.selectedBill?.id ?? this.editingPayment?.billId ?? null;
    if (billId) {
      this.router.navigate(['/bills', billId]);
    } else {
      this.router.navigate(['/payments']);
    }
  }
}