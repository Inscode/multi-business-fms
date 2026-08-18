import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectorRef, Component, HostListener, Inject } from '@angular/core';
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
import { Auth } from '../../../core/services/auth';
import { compressImage } from '../../../core/utils/image-compress';
import { ReceiptRequirement } from '../../../core/services/payment';

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

  /** Upload state per row, so one slow photo does not lock the others. */
  uploading: Record<number, boolean> = {};
  previews: Record<number, string> = {};
  rowError: Record<number, string> = {};
  /** What is already photographed for each bill, keyed by row. */
  coverage: Record<number, ReceiptRequirement> = {};

  get isAdminUser(): boolean {
    const r = this.auth.getRole();
    return r === 'ADMIN' || r === 'OWNER';
  }

  /** Rows still needing a photo — the thing that blocks submitting. */
  get rowsMissingPhoto(): number[] {
    if (this.isAdminUser) return [];
    return this.billForms.controls
      .map((c, i) => ({ c, i }))
      .filter(({ c, i }) => !c.get('receiptImageUrl')?.value && !this.coverage[i]?.canShare)
      .map(({ i }) => i);
  }

  get anyUploading(): boolean {
    return Object.values(this.uploading).some(Boolean);
  }

  isCovered(i: number): boolean {
    return !this.billForms.at(i).get('receiptImageUrl')?.value
        && !!this.coverage[i]?.canShare;
  }

  /**
   * Takes a photo for one row, from the picker or from a pasted screenshot.
   *
   * <p>Kept per row because a combined payment's whole difficulty is that it is several
   * collections at once, and attaching to the wrong one is silent.
   */
  onRowImage(i: number, file: File | null): void {
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      this.rowError[i] = 'That is not an image.';
      this.cdr.markForCheck();
      return;
    }
    this.rowError[i] = '';

    const reader = new FileReader();
    reader.onload = () => { this.previews[i] = String(reader.result); this.cdr.markForCheck(); };
    reader.readAsDataURL(file);

    this.uploading[i] = true;
    this.cdr.markForCheck();
    compressImage(file).then(result => {
      this.paymentService.uploadImage(result.file).subscribe({
        next: (url) => {
          this.billForms.at(i).get('receiptImageUrl')?.setValue(url);
          this.uploading[i] = false;
          this.cdr.markForCheck();
        },
        error: () => {
          this.uploading[i] = false;
          this.rowError[i] = 'Upload failed — try again.';
          this.cdr.markForCheck();
        },
      });
    });
  }

  onRowPicked(i: number, e: Event): void {
    const input = e.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    this.onRowImage(i, file);
  }

  /**
   * The row a pasted screenshot lands on.
   *
   * <p>Needed because a paste has no target of its own: the event tells you an image
   * arrived, not which of three bills it belongs to. Guessing wrong is silent — the
   * photo attaches to the wrong collection and looks perfectly correct — so the target
   * is always shown on screen before the paste happens.
   */
  pasteRow = 0;

  /** Clicking anywhere in a row aims the next paste at it. */
  focusRow(i: number): void {
    this.pasteRow = i;
    this.cdr.markForCheck();
  }

  /**
   * Ctrl+V anywhere in the dialog, onto whichever row is aimed at.
   *
   * <p>Bound to the document rather than to a row, because a paste event only reaches
   * the focused element and a row is not focusable — the shortcut would do nothing the
   * first time anyone tried it. The aim moves on its own to the next row still wanting a
   * photo, so three bills can be pasted one after another without touching the mouse.
   */
  @HostListener('document:paste', ['$event'])
  onPaste(e: ClipboardEvent): void {
    const items = e.clipboardData?.items;
    if (!items) return;
    for (const item of Array.from(items)) {
      if (item.kind !== 'file' || !item.type.startsWith('image/')) continue;
      const file = item.getAsFile();
      if (!file) continue;
      e.preventDefault();
      const target = this.pasteRow;
      this.onRowImage(target, file);
      this.advancePasteRow(target);
      return;
    }
  }

  /** Moves the aim to the next row still wanting a photo, wrapping once. */
  private advancePasteRow(from: number): void {
    const n = this.billForms.length;
    for (let step = 1; step <= n; step++) {
      const i = (from + step) % n;
      if (this.needsPhoto(i)) { this.pasteRow = i; return; }
    }
  }

  /** Whether this row still wants a photo of its own. */
  needsPhoto(i: number): boolean {
    if (this.isAdminUser) return false;
    if (this.isCovered(i)) return false;
    return !this.billForms.at(i).get('receiptImageUrl')?.value && !this.uploading[i];
  }

  clearRowImage(i: number): void {
    this.billForms.at(i).get('receiptImageUrl')?.setValue(null);
    delete this.previews[i];
    this.rowError[i] = '';
    this.cdr.markForCheck();
  }

  constructor(
    private dialogRef: MatDialogRef<BulkPaymentDialog>,
    @Inject(MAT_DIALOG_DATA) public data: BulkPaymentDialogData,
    fb: FormBuilder,
    private paymentService: Payment,
    private cdr: ChangeDetectorRef,
    private auth: Auth,
  ) {
    const today = new Date();

    const billControls = data.bills.map(b =>
      fb.group({
        billId: [b.id],
        amount: [b.balanceRemaining, [Validators.required, Validators.min(0.01)]],
        // Per bill, not per payment. One cheque may settle three bills, but the three
        // are separate pieces of paper signed separately, and a photo of one is not
        // evidence for the other two.
        receiptImageUrl: [null as string | null],
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

    this.loadCoverage();

    // Aimed at the first row wanting a photo, so the first Ctrl+V lands somewhere
    // sensible without anyone having chosen a row.
    this.pasteRow = 0;
  }

  /** What each bill already has on file, read once when the dialog opens. */
  private loadCoverage(): void {
    this.data.bills.forEach((b, i) => {
      if (!b?.id) return;
      this.paymentService.getReceiptRequirement(b.id).subscribe({
        next: (r) => { this.coverage[i] = r; this.cdr.markForCheck(); },
        error: () => {},
      });
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

    // Refused here as well as on the server. A combined payment is a long form, and
    // being told after submitting which of three bills lacked a photo is the worst
    // moment to find out.
    const missing = this.rowsMissingPhoto;
    if (missing.length) {
      const numbers = missing.map(i => this.data.bills[i].billNumber).join(', ');
      this.errorMsg = missing.length === 1
        ? `Attach a photo of bill ${numbers} — each bill needs its own, since each was `
          + 'signed separately.'
        : `Attach a photo of each bill: ${numbers}. Each was signed separately, so one `
          + 'photo cannot cover them all.';
      return;
    }
    if (this.anyUploading) {
      this.errorMsg = 'Wait for the photos to finish uploading.';
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