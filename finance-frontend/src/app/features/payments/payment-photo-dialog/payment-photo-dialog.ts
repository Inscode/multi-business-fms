import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Inject, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Payment, PaymentResponse } from '../../../core/services/payment';
import { compressImage } from '../../../core/utils/image-compress';

export interface PaymentPhotoData {
  payment: PaymentResponse;
  /** 'confirm' adds the admin's own upload and a Confirm action; 'view' is read-only. */
  mode: 'view' | 'confirm';
}

/**
 * The photographs behind a payment, and — when confirming — the chance to add one.
 *
 * <p>Both are shown to whoever opens it. The accountant's photo is the evidence for
 * the figure they entered; the admin's is what they saw when checking it. An admin
 * confirming without looking at the first would make the requirement pointless, so it
 * is put in front of them at the moment they confirm rather than left on a detail page.
 */
@Component({
  selector: 'app-payment-photo-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule, MatIconModule,
            MatProgressSpinnerModule],
  templateUrl: './payment-photo-dialog.html',
  styleUrl: './payment-photo-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaymentPhotoDialog {
  private paymentService = inject(Payment);
  private cdr = inject(ChangeDetectorRef);

  uploading = false;
  uploadError = '';
  /** The admin's own photo, if they attach one before confirming. */
  confirmUrl: string | null = null;
  confirmPreview: string | null = null;

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: PaymentPhotoData,
    private ref: MatDialogRef<PaymentPhotoDialog>,
  ) {}

  get p(): PaymentResponse { return this.data.payment; }
  get isConfirm(): boolean { return this.data.mode === 'confirm'; }

  onPick(e: Event): void {
    const input = e.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      this.uploadError = 'That is not an image.';
      this.cdr.markForCheck();
      return;
    }

    this.uploadError = '';
    const reader = new FileReader();
    reader.onload = () => { this.confirmPreview = String(reader.result); this.cdr.markForCheck(); };
    reader.readAsDataURL(file);

    this.uploading = true;
    this.cdr.markForCheck();
    compressImage(file).then(result => this.send(result.file));
  }

  private send(file: File): void {
    this.paymentService.uploadImage(file).subscribe({
      next: (url) => { this.confirmUrl = url; this.uploading = false; this.cdr.markForCheck(); },
      error: () => {
        this.uploading = false;
        this.uploadError = 'Upload failed — check the connection and try again.';
        this.cdr.markForCheck();
      },
    });
  }

  /** Opens the full-size image, since a thumbnail rarely settles a question about a figure. */
  openFull(url: string): void {
    window.open(url, '_blank', 'noopener');
  }

  confirm(): void {
    // Waiting matters: confirming mid-upload would save without the photo attached.
    if (this.uploading) return;
    this.ref.close({ confirmed: true, confirmImageUrl: this.confirmUrl ?? undefined });
  }

  close(): void { this.ref.close(); }
}
