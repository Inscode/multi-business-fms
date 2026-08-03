import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Payment } from '../../../core/services/payment';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-return-cheque-dialog',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatIconModule
  ],
  templateUrl: './return-cheque-dialog.html',
  styleUrl: './return-cheque-dialog.scss',
})
export class ReturnChequeDialog {
  form: FormGroup;
  loading = false;
  errorMsg = '';

  constructor(
    private fb: FormBuilder,
    private paymentService: Payment,
    private dialogRef: MatDialogRef<ReturnChequeDialog>,
    @Inject(MAT_DIALOG_DATA) public data: {paymentId: number; billNumber: string},
    private cdr: ChangeDetectorRef
  ) {
    this.form = this.fb.group({
      returnReason: ['', [Validators.required, Validators.minLength(5)]]
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading = true;
    this.errorMsg = '';

    this.paymentService.markChequeReturned(
      this.data.paymentId, 
      this.form.value.returnReason
    ).subscribe({
      next: () => this.dialogRef.close(true),
      error: (e) => {
        this.errorMsg = e?.error?.message ?? 'Failed to process cheque return.';
        this.loading = false;
        this.cdr.markForCheck();
      }
    });
  }

  cancel(): void {
    this.dialogRef.close(false);
  }
}
