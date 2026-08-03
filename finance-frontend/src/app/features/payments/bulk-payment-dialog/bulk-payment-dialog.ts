import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectorRef, Component, Inject } from '@angular/core';
import { localDateStr } from '../../../core/utils/date-utils';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { Payment } from '../../../core/services/payment';
import { BANKS, AREAS } from '../../../core/constants/payment-options';

export interface BulkPaymentDialogData {
  bills: any[];
}

@Component({
  selector: 'app-bulk-payment-dialog',
  templateUrl: './bulk-payment-dialog.html',
  styleUrl: './bulk-payment-dialog.scss',
  standalone: true,
  imports: [
    CommonModule,
    DecimalPipe,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDividerModule,
    MatProgressSpinnerModule,
    MatDatepickerModule,
  ],
})
export class BulkPaymentDialog {
  form: FormGroup;
  submitting = false;
  errorMsg = '';
  banks = BANKS;
  areas = AREAS;

  get billForms(): FormArray {
    return this.form.get('bills') as FormArray;
  }

  get paymentType(): string {
    return this.form.get('paymentType')?.value ?? '';
  }

  get isCheque(): boolean     { return this.paymentType === 'CHEQUE'; }
  get isBankTransfer(): boolean { return this.paymentType === 'BANK_TRANSFER'; }

  get total(): number {
    return this.billForms.controls.reduce(
      (sum, c) => sum + (Number(c.get('amount')?.value) || 0), 0
    );
  }

  constructor(
    private dialogRef: MatDialogRef<BulkPaymentDialog>,
    @Inject(MAT_DIALOG_DATA) public data: BulkPaymentDialogData,
    fb: FormBuilder,
    private paymentService: Payment,
    private cdr: ChangeDetectorRef,
  ) {
    const today = new Date();

    const billControls = data.bills.map(b =>
      fb.group({
        billId: [b.id],
        amount: [b.balanceRemaining, [Validators.required, Validators.min(0.01)]],
      })
    );

    this.form = fb.group({
      bills:           fb.array(billControls),
      paymentType:     ['CASH', Validators.required],
      chequeNumber:    [''],
      bankName:        [''],
      branchName:      [''],
      chequeDate:      [null],
      referenceNumber: [''],
      paymentDate:     [today],
      notes:           [''],
    });
  }

  onTypeChange(): void {
    const chequeNum  = this.form.get('chequeNumber')!;
    const bankName   = this.form.get('bankName')!;
    const chequeDate = this.form.get('chequeDate')!;

    chequeNum.clearValidators();
    bankName.clearValidators();
    chequeDate.clearValidators();

    if (this.isCheque) {
      chequeNum.setValidators(Validators.required);
      bankName.setValidators(Validators.required);
      chequeDate.setValidators(Validators.required);
    } else if (this.isBankTransfer) {
      bankName.setValidators(Validators.required);
    }

    chequeNum.updateValueAndValidity();
    bankName.updateValueAndValidity();
    chequeDate.updateValueAndValidity();
  }

  private toDateString(value: Date | string | null): string | undefined {
    if (!value) return undefined;
    if (typeof value === 'string') return value;
    return localDateStr(value);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting = true;
    this.errorMsg = '';

    const v = this.form.value;
    this.paymentService.enterBulkPayment({
      bills:           v.bills,
      paymentType:     v.paymentType,
      chequeNumber:    v.chequeNumber    || undefined,
      bankName:        v.bankName        || undefined,
      branchName:      v.branchName      || undefined,
      chequeDate:      this.toDateString(v.chequeDate),
      referenceNumber: v.referenceNumber || undefined,
      paymentDate:     this.toDateString(v.paymentDate),
      notes:           v.notes           || undefined,
    }).subscribe({
      next: () => this.dialogRef.close(true),
      error: (e) => {
        this.errorMsg = e?.error?.message ?? 'Failed to submit payment. Please try again.';
        this.submitting = false;
        this.cdr.markForCheck();
      },
    });
  }

  close(): void { this.dialogRef.close(); }
}