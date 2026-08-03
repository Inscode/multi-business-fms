import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { BillReminderService } from '../../../core/services/bill-reminder';

export interface ReminderDialogData {
  billId: number;
  billNumber: string;
  customerName: string;
}

@Component({
  selector: 'app-reminder-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatDatepickerModule,
    MatNativeDateModule,
  ],
  templateUrl: './reminder-dialog.html',
  styleUrl: './reminder-dialog.scss',
})
export class ReminderDialog {
  form: FormGroup;
  saving = false;
  errorMsg = '';

  periods = ['MORNING', 'AFTERNOON', 'EVENING', 'ANYTIME'];
  minDate = new Date();

  constructor(
    private fb: FormBuilder,
    private reminderService: BillReminderService,
    private dialogRef: MatDialogRef<ReminderDialog>,
    @Inject(MAT_DIALOG_DATA) public data: ReminderDialogData,
    private cdr: ChangeDetectorRef
  ) {
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);

    this.form = this.fb.group({
      reminderDate: [tomorrow, Validators.required],
      period:       ['ANYTIME', Validators.required],
      note:         [''],
    });
  }

  submit(): void {
    if (this.form.invalid) return;

    this.saving   = true;
    this.errorMsg = '';

    const value = this.form.value;
    const date  = value.reminderDate as Date;

    this.reminderService.create({
      billId:       this.data.billId,
      reminderDate: `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`,
      period:       value.period,
      note:         value.note || undefined,
    }).subscribe({
      next: () => this.dialogRef.close(true),
      error: (e) => {
        this.errorMsg = e?.error?.message ?? 'Failed to save reminder.';
        this.saving   = false;
        this.cdr.markForCheck();
      },
    });
  }

  cancel(): void {
    this.dialogRef.close(false);
  }
}