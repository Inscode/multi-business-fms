import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, HostListener, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepicker, MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { ExpenseService } from '../../../core/services/expense';
import { SalaryRecipientResponse, SalaryService } from '../../../core/services/salary';
import { Worker, WorkerResponse } from '../../../core/services/worker';
import { compressImage, describeSaving } from '../../../core/utils/image-compress';
import { localDateStr } from '../../../core/utils/date-utils';

@Component({
  selector: 'app-add-expense',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatProgressSpinnerModule,
    MatIconModule,
  ],
  templateUrl: './add-expense.html',
  styleUrl: './add-expense.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddExpense implements OnInit {
  form: FormGroup;
  submitting = false;
  errorMsg = '';
  successMsg = '';
  workers: WorkerResponse[] = [];
  salaryRecipients: SalaryRecipientResponse[] = [];
  salaryMonthPickerDate = new Date();

  businesses = ['RAINCO', 'PLASTIC'];
  receiptImageUrl = '';
  uploadingPhoto = false;
  photoError = '';
  photoSizeNote = '';
  categories = [
    { value: 'TEA',        label: 'Tea',          hint: 'Morning / evening tea' },
    { value: 'SALARY',     label: 'Salary',       hint: 'Record a salary payment by recipient and salary month' },
    { value: 'DRIVER_SALARY', label: 'Driver Salary',  hint: 'Temporary driver salary — needs review if over Rs 3,000' },
    { value: 'OTHER',         label: 'Other',          hint: 'Unplanned expense — needs review' },
  ];

  get needsReview(): boolean {
    const cat = this.form.get('category')?.value;
    const amt = Number(this.form.get('amount')?.value ?? 0);
    if (cat === 'DRIVER_SALARY') return amt > 3000;
    return cat === 'REPAIR' || cat === 'OTHER';
  }

  get isSalaryPayment(): boolean { return this.form.get('category')?.value === 'SALARY'; }

  get canSubmit(): boolean {
    return !this.form.invalid && !this.submitting && !this.uploadingPhoto
      && (!this.requiresReceipt || !!this.receiptImageUrl)
      && (!this.isSalaryPayment || (!!this.form.get('salaryRecipientId')?.value && !!this.form.get('salaryMonth')?.value));
  }

  get salaryMonthLabel(): string {
    const value = this.form.get('salaryMonth')?.value as string | null;
    if (!value) return '';
    const [year, month] = value.split('-').map(Number);
    return new Intl.DateTimeFormat(undefined, { month: 'long', year: 'numeric' }).format(new Date(year, month - 1, 1));
  }

  chooseSalaryMonth(month: Date, picker: MatDatepicker<Date>): void {
    this.salaryMonthPickerDate = new Date(month.getFullYear(), month.getMonth(), 1);
    this.form.patchValue({ salaryMonth: `${month.getFullYear()}-${String(month.getMonth() + 1).padStart(2, '0')}` });
    picker.close();
  }

  get requiresReceipt(): boolean {
    const category = this.form.get('category')?.value;
    return !!category && !['TEA', 'SALARY', 'DRIVER_SALARY'].includes(category);
  }

  uploadReceipt(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    input.value = '';
    this.acceptReceipt(file);
  }

  @HostListener('document:paste', ['$event'])
  onPaste(event: ClipboardEvent): void {
    if (!this.requiresReceipt) return;
    const image = Array.from(event.clipboardData?.items ?? []).find(item => item.kind === 'file' && item.type.startsWith('image/'));
    const file = image?.getAsFile();
    if (!file) return;
    event.preventDefault();
    this.acceptReceipt(file);
  }

  private acceptReceipt(file: File): void {
    this.photoError = '';
    if (!file.type.startsWith('image/')) { this.photoError = 'Choose an image file.'; return; }
    this.uploadingPhoto = true;
    this.cdr.markForCheck();
    compressImage(file, 150 * 1024).then(compressed => {
      this.photoSizeNote = `Photo size: ${describeSaving(compressed)}`;
      if (compressed.bytes > 10 * 1024 * 1024) {
        this.photoError = 'Compressed photo is still too large. Choose a smaller image.';
        this.uploadingPhoto = false; this.cdr.markForCheck(); return;
      }
      this.expenseService.uploadImage(compressed.file).subscribe({
        next: result => { this.receiptImageUrl = result.url; this.uploadingPhoto = false; this.cdr.markForCheck(); },
        error: () => { this.photoError = 'Could not upload the expense photo.'; this.uploadingPhoto = false; this.cdr.markForCheck(); },
      });
    });
  }

  clearReceipt(): void { this.receiptImageUrl = ''; this.photoError = ''; this.photoSizeNote = ''; }

  constructor(
    private fb: FormBuilder,
    private expenseService: ExpenseService,
    private salaryService: SalaryService,
    private workerService: Worker,
    private router: Router,
    private cdr: ChangeDetectorRef,
  ) {
    this.form = this.fb.group({
      business:    ['RAINCO', Validators.required],
      category:    ['', Validators.required],
      amount:      ['', [Validators.required, Validators.min(1)]],
      date:        [new Date(), Validators.required],
      description: [''],
      workerId:    [null],
      salaryRecipientId: [null],
      salaryMonth: [localDateStr().substring(0, 7)],
    });
    this.form.get('category')?.valueChanges.subscribe(() => {
      if (!this.requiresReceipt) this.clearReceipt();
    });
  }

  ngOnInit(): void {
    this.salaryService.getActiveRecipients().subscribe({
      next: recipients => { this.salaryRecipients = recipients; this.cdr.markForCheck(); },
      error: () => {},
    });
    this.workerService.getAllWorkers().subscribe({
      next: (w: WorkerResponse[]) => {
        this.workers = w.filter(w => w.active);
        this.cdr.detectChanges();
      },
      error: () => {},
    });
  }

  submit(): void {
    if (!this.canSubmit) return;
    this.submitting = true;
    this.errorMsg = '';
    this.successMsg = '';

    const v = this.form.getRawValue();
    const date: Date = v.date;
    const paymentDate = `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;

    if (this.isSalaryPayment) {
      this.salaryService.createPayment({
        recipientId: v.salaryRecipientId,
        month: v.salaryMonth,
        amount: Number(v.amount),
        paymentDate,
        notes: v.description || undefined,
      }).subscribe({
        next: payment => {
          this.submitting = false;
          this.successMsg = payment.status === 'PENDING_APPROVAL'
            ? 'Salary submitted for approval. The expense fund will be reduced after approval.'
            : 'Salary recorded. The amount has been deducted from the expense fund.';
          this.form.reset({
            business: 'RAINCO', category: '', amount: '', date: new Date(), description: '',
            workerId: null, salaryRecipientId: null, salaryMonth: localDateStr().substring(0, 7),
          });
          this.salaryMonthPickerDate = new Date();
          this.cdr.markForCheck();
        },
        error: err => {
          this.submitting = false;
          this.errorMsg = err?.error?.message ?? 'Failed to record salary payment.';
          this.cdr.markForCheck();
        },
      });
      return;
    }

    this.expenseService.create({
      business:    v.business,
      category:    v.category,
      amount:      v.amount,
      date:        paymentDate,
      description: v.description || undefined,
      workerId:    v.workerId || undefined,
      receiptImageUrl: this.requiresReceipt ? this.receiptImageUrl || undefined : undefined,
    }).subscribe({
      next: () => {
        this.submitting = false;
        this.router.navigate(['/expenses']);
      },
      error: (err) => {
        this.submitting = false;
        this.errorMsg = err?.error?.message ?? 'Failed to save expense.';
        this.cdr.detectChanges();
      },
    });
  }
}
