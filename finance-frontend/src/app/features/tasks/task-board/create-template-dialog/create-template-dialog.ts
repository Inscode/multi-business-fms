import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { TaskTemplate } from '../../models/task.models';

export interface CreateTemplateDialogData {
  template?: TaskTemplate;
}

@Component({
  selector: 'app-create-template-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatSlideToggleModule,
  ],
  templateUrl: './create-template-dialog.html',
  styles: [`
    mat-dialog-content { padding-top: 8px !important; min-width: 480px; max-width: 540px; }
    .form-row { display: flex; gap: 16px; margin-bottom: 4px; }
    .field { flex: 1; }
    .field-full { width: 100%; }
    .toggle-row { padding: 8px 0 12px; }
    .section-label { font-size: 11px; font-weight: 600; color: #90a4ae; text-transform: uppercase;
                     letter-spacing: 0.8px; margin: 12px 0 6px; }
  `],
})
export class CreateTemplateDialog {
  form: FormGroup;
  isEdit: boolean;

  taskTypes = [
    { value: 'EXPENSE',       label: 'Expense' },
    { value: 'STOCK',         label: 'Stock entry' },
    { value: 'ATTENDANCE',    label: 'Attendance' },
    { value: 'BILLS',         label: 'Bills' },
    { value: 'DEDUCTION',     label: 'Deduction' },
    { value: 'UPLOAD',        label: 'Upload' },
    { value: 'DELIVERY_BILLS',label: 'Delivery bills' },
    { value: 'PAYMENTS',      label: 'Payments' },
  ];

  constructor(
    private fb: FormBuilder,
    private dialogRef: MatDialogRef<CreateTemplateDialog>,
    @Inject(MAT_DIALOG_DATA) public data: CreateTemplateDialogData,
  ) {
    this.isEdit = !!data?.template;
    const t = data?.template;
    this.form = this.fb.group({
      title:         [t?.title         ?? '',                Validators.required],
      description:   [t?.description   ?? '',                Validators.required],
      assignedRole:  [t?.assignedRole  ?? 'ACCOUNTANT',      Validators.required],
      frequency:     [t?.frequency     ?? 'DAILY',           Validators.required],
      taskType:      [t?.taskType      ?? 'BILLS',           Validators.required],
      businessUnit:  [t?.businessUnit  || null],
      dueTime:       [t?.dueTime       ?? ''],
      urgencyLevel:  [t?.urgencyLevel  ?? 'MEDIUM',          Validators.required],
      active:        [t?.active        ?? true],
    });
  }

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const payload = this.form.getRawValue();
    // Ensure empty string is never sent as enum value — backend expects null or a valid enum
    if (!payload.businessUnit) payload.businessUnit = null;
    if (!payload.dueTime)      payload.dueTime      = null;
    this.dialogRef.close(payload);
  }
}
