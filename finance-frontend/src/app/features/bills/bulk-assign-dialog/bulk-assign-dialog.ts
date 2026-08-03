import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectorRef, Component, Inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { Bill, BillResponse } from '../../../core/services/bill';
import { WorkerResponse } from '../../../core/services/worker';

export interface BulkAssignDialogData {
  bills: BillResponse[];
  workers: WorkerResponse[];
}

@Component({
  selector: 'app-bulk-assign-dialog',
  templateUrl: './bulk-assign-dialog.html',
  styleUrl: './bulk-assign-dialog.scss',
  standalone: true,
  imports: [
    CommonModule,
    DecimalPipe,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
    MatDividerModule,
    MatProgressSpinnerModule,
  ],
})
export class BulkAssignDialog {
  workerCtrl = new FormControl<number | null>(null, Validators.required);
  submitting = false;
  errorMsg = '';

  constructor(
    private dialogRef: MatDialogRef<BulkAssignDialog>,
    @Inject(MAT_DIALOG_DATA) public data: BulkAssignDialogData,
    private billService: Bill,
    private cdr: ChangeDetectorRef,
  ) {}

  submit(): void {
    if (this.workerCtrl.invalid || !this.workerCtrl.value) {
      this.workerCtrl.markAsTouched();
      return;
    }

    this.submitting = true;
    this.errorMsg = '';

    const billIds = this.data.bills.map(b => b.id);
    this.billService.bulkAssignBills(billIds, this.workerCtrl.value).subscribe({
      next: () => this.dialogRef.close(true),
      error: (e) => {
        this.errorMsg = e?.error?.message ?? 'Failed to assign bills. Please try again.';
        this.submitting = false;
        this.cdr.markForCheck();
      },
    });
  }

  close(): void { this.dialogRef.close(); }
}
