import { CommonModule } from '@angular/common';
import { Component, Inject, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
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
  ],
  templateUrl: './request-edit-dialog.html',
  styleUrl: './request-edit-dialog.scss',
})
export class RequestEditDialog implements OnInit {
  form!: FormGroup;
  submitting = false;
  errorMsg = '';

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

  get isBill(): boolean { return this.data.type === 'BILL'; }

  get isCheque(): boolean {
    return this.form?.get('paymentType')?.value === 'CHEQUE';
  }

  constructor(
    private fb: FormBuilder,
    private editRequestService: EditRequestService,
    public dialogRef: MatDialogRef<RequestEditDialog>,
    @Inject(MAT_DIALOG_DATA) public data: RequestEditDialogData,
  ) {}

  ngOnInit(): void {
    const c = this.data.current;
    if (this.isBill) {
      this.form = this.fb.group({
        customerName: [c['customerName'] ?? '', Validators.required],
        totalAmount:  [c['totalAmount']  ?? null, [Validators.required, Validators.min(0.01)]],
        billType:     [c['billType']     ?? null, Validators.required],
        division:     [c['division']     ?? null],
        area:         [c['area']         ?? null],
        billDate:     [c['billDate'] ? new Date(c['billDate']) : null],
        notes:        [c['notes']        ?? ''],
        reason:       ['', Validators.required],
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

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }

    this.submitting = true;
    this.errorMsg = '';

    const { reason, ...changes } = this.form.value;

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
      },
    });
  }
}