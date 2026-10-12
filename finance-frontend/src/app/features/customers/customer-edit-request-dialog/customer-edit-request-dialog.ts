import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { CustomerResponse } from '../../../core/services/customer';
import { EditRequestService } from '../../../core/services/edit-request';

export interface CustomerEditRequestData {
  customer: CustomerResponse;
}

@Component({
  selector: 'app-customer-edit-request-dialog',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatDialogModule, MatButtonModule,
    MatFormFieldModule, MatIconModule, MatInputModule, MatSelectModule,
  ],
  templateUrl: './customer-edit-request-dialog.html',
  styleUrl: './customer-edit-request-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CustomerEditRequestDialog {
  submitting = false;
  errorMsg = '';

  areas = [
    'Ambagasdowa', 'Badalkumbura', 'Badulla', 'Bandarawela', 'Beragala',
    'Bogakumbura', 'Boralanda', 'Demodara', 'Diyatalawa', 'Ella',
    'Etampitiya', 'Haldummulla', 'Hali-Ela', 'Hasalaka', 'Haputale',
    'Hopton', 'Kandaketiya', 'Keppatipola', 'Kumbalwela', 'Lunugala',
    'Lunuwatta', 'Mahiyanganaya', 'Meegahakivula', 'Passara',
    'Uva-Paranagama', 'Welimada',
  ];

  form!: FormGroup;

  constructor(
    private fb: FormBuilder,
    private requests: EditRequestService,
    private cdr: ChangeDetectorRef,
    public ref: MatDialogRef<CustomerEditRequestDialog>,
    @Inject(MAT_DIALOG_DATA) public data: CustomerEditRequestData,
  ) {
    this.form = this.fb.group({
      name: [this.data.customer.name, [Validators.required, Validators.minLength(2)]],
      area: [this.data.customer.area ?? '', Validators.required],
      phone: [this.data.customer.phone ?? '', Validators.required],
      phone2: [this.data.customer.phone2 ?? ''],
      reason: ['', Validators.required],
    });
  }

  get areaOptions(): string[] {
    const options = this.areas.map(area => area.toUpperCase());
    const current = this.data.customer.area?.trim().toUpperCase();
    return current && !options.includes(current) ? [current, ...options] : options;
  }

  get hasChanges(): boolean {
    return Object.keys(this.getChanges()).length > 0;
  }

  private getChanges(): Record<string, string | null> {
    const raw = this.form.getRawValue();
    const original = this.data.customer;
    const normalized: Record<string, string | null> = {
      name: raw.name?.trim() ?? '',
      area: raw.area?.trim().toUpperCase() ?? '',
      phone: raw.phone?.trim() ?? '',
      phone2: raw.phone2?.trim() || null,
    };
    // Customer names are stored in uppercase by the backend, so changing only
    // the letter case is not a meaningful edit request.
    const previous: Record<string, string | null> = {
      name: original.name.trim().toUpperCase(),
      area: (original.area ?? '').trim().toUpperCase(),
      phone: (original.phone ?? '').trim(),
      phone2: original.phone2?.trim() || null,
    };
    return Object.fromEntries(
      Object.entries(normalized).filter(([key, value]) =>
        (key === 'name' ? value?.toUpperCase() : value) !== previous[key],
      ),
    );
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue();
    const original = this.data.customer;
    const changes = this.getChanges();

    if (Object.keys(changes).length === 0) {
      this.errorMsg = 'No customer details have changed.';
      return;
    }

    this.submitting = true;
    this.errorMsg = '';
    this.requests.create({
      type: 'CUSTOMER',
      targetId: original.id,
      targetRef: original.name,
      requestedChanges: JSON.stringify(changes),
      reason: raw.reason?.trim() ?? '',
    }).subscribe({
      next: () => this.ref.close(true),
      error: err => {
        this.errorMsg = err?.error?.message ?? 'Could not send the change request.';
        this.submitting = false;
        this.cdr.markForCheck();
      },
    });
  }
}
