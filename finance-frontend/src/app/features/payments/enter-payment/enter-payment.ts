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
import { compressImage, describeSaving } from '../../../core/utils/image-compress';
import { Router } from '@angular/router';
import { BANKS, AREAS } from '../../../core/constants/payment-options';
import { ChequeAgeBand, chequeAgeBand, chequeAgeDays, chequeAgeLabel } from '../../../core/utils/cheque-age';

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

  businesses   = ['RAINCO', 'RETAIL_SHOP', 'PLASTIC', 'HARDWARE', 'STATIONERY', 'MIX'];
  paymentTypes = ['CASH', 'CHEQUE', 'BANK_TRANSFER'];
  banks = BANKS;
  areas = AREAS;

  get isCheque(): boolean {
    return this.form.get('paymentType')?.value === 'CHEQUE';
  }

  get isBankTransfer(): boolean {
    return this.form.get('paymentType')?.value === 'BANK_TRANSFER';
  }

  /** Bill date backing the cheque-age hint — from the picked bill, or the payment being edited. */
  get selectedBillDate(): string | null {
    return this.selectedBill?.billDate ?? this.editingPayment?.billDate ?? null;
  }

  /** Days between the bill date and the cheque date currently in the form. */
  get chequeAge(): number | null {
    if (!this.isCheque) return null;
    const chequeDate = this.form.get('chequeDate')?.value as Date | null;
    if (!chequeDate) return null;
    return chequeAgeDays(this.selectedBillDate, this.toLocalDateStr(new Date(chequeDate)));
  }

  ageBand(days: number): ChequeAgeBand { return chequeAgeBand(days); }
  ageLabel(days: number): string { return chequeAgeLabel(days); }

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

  // ── Photo of the bill ───────────────────────────────────────────────
  // Required of an accountant: they are recording money already collected, in the
  // field, and this is the only thing tying the figure to the paper the customer
  // signed. An admin may attach one but is not asked to.

  receiptFile: File | null = null;
  receiptPreview: string | null = null;
  receiptUrl: string | null = null;
  uploadingImage = false;
  imageError = '';
  /** "3.8 MB → 240 KB", so it is clear the big photo was not sent as-is. */
  imageSizeNote = '';

  get isAdminUser(): boolean {
    const r = this.auth.getRole();
    return r === 'ADMIN' || r === 'OWNER';
  }

  /** Everyone but an admin has to attach one. */
  get imageRequired(): boolean { return !this.isAdminUser; }

  onReceiptPicked(e: Event): void {
    const input = e.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      this.imageError = 'That is not an image — take a photo of the bill.';
      return;
    }

    this.imageError = '';
    this.receiptFile = file;
    this.receiptUrl = null;
    // Shown from the local file straight away; the upload catches up behind it.
    const reader = new FileReader();
    reader.onload = () => { this.receiptPreview = String(reader.result); this.cdr?.markForCheck?.(); };
    reader.readAsDataURL(file);

    // Shrunk before it leaves the phone: the upload is the slow part on mobile data,
    // and a full-resolution photograph of a bill is no more readable than a small one.
    this.uploadingImage = true;
    compressImage(file).then(result => {
      this.imageSizeNote = describeSaving(result);
      this.paymentService.uploadImage(result.file).subscribe({
        next: (url) => { this.receiptUrl = url; this.uploadingImage = false; this.cdr?.markForCheck?.(); },
        error: () => {
          this.uploadingImage = false;
          this.imageError = 'The photo could not be uploaded. Check the connection and try again.';
          this.cdr?.markForCheck?.();
        },
      });
    });
  }

  clearReceipt(): void {
    this.receiptFile = null;
    this.receiptPreview = null;
    this.receiptUrl = null;
    this.imageError = '';
    this.imageSizeNote = '';
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

    // Refused here as well as on the server: a failed save after a long form on a
    // phone is worse than being told before it is sent.
    if (this.imageRequired && !this.receiptUrl) {
      this.errorMsg = this.uploadingImage
        ? 'Wait for the photo to finish uploading.'
        : 'Attach a photo of the bill before saving this payment.';
      return;
    }

    this.loading  = true;
    this.errorMsg = '';

    const fv = this.form.value;
    const payload = {
      ...fv,
      receiptImageUrl: this.receiptUrl ?? undefined,
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