import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, HostListener, Inject, inject } from '@angular/core';
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
  zoomImageUrl: string | null = null;
  zoomLevel = 1;
  zoomOffsetX = 0;
  zoomOffsetY = 0;
  private dragStart: { x: number; y: number; offsetX: number; offsetY: number } | null = null;

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: PaymentPhotoData,
    private ref: MatDialogRef<PaymentPhotoDialog>,
  ) {}

  get p(): PaymentResponse { return this.data.payment; }
  get isConfirm(): boolean { return this.data.mode === 'confirm'; }

  /** Prefer the current response field, with the legacy API name as a fallback. */
  get amountToConfirm(): number | null {
    const response = this.p as unknown as {
      paymentAmount?: number | string | null;
      amount?: number | string | null;
    };
    const raw: unknown = response.paymentAmount ?? response.amount;
    if (raw === null || raw === undefined || raw === '') return null;
    const amount = Number(raw);
    return Number.isFinite(amount) ? amount : null;
  }

  onPick(e: Event): void {
    const input = e.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    this.accept(file);
  }

  /**
   * Accepts a screenshot pasted from the clipboard.
   *
   * <p>Bound to the document rather than the drop zone: a paste event only reaches the
   * focused element, and the zone is a label nobody thinks to click first — binding it
   * there makes the shortcut do nothing the first time anyone tries it. The dialog is
   * modal and holds one image slot, so a paste while it is open can only mean this one.
   */
  @HostListener('document:paste', ['$event'])
  onPaste(e: ClipboardEvent): void {
    if (!this.isConfirm) return;      // the view-only mode has nothing to paste into
    const items = e.clipboardData?.items;
    if (!items) return;
    for (const item of Array.from(items)) {
      if (item.kind !== 'file' || !item.type.startsWith('image/')) continue;
      const file = item.getAsFile();
      if (!file) continue;
      e.preventDefault();
      this.accept(file);
      return;
    }
  }

  /** One path for the file picker and a pasted screenshot alike. */
  private accept(file: File | null): void {
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

  /** Open the receipt in an in-dialog viewer so the admin can inspect it without leaving confirmation. */
  openZoom(url: string): void {
    this.zoomImageUrl = url;
    this.zoomLevel = 1;
    this.zoomOffsetX = 0;
    this.zoomOffsetY = 0;
  }

  closeZoom(): void {
    this.zoomImageUrl = null;
    this.zoomLevel = 1;
    this.zoomOffsetX = 0;
    this.zoomOffsetY = 0;
    this.dragStart = null;
  }

  changeZoom(delta: number): void {
    this.zoomLevel = Math.min(4, Math.max(1, this.zoomLevel + delta));
  }

  onZoomWheel(event: WheelEvent): void {
    event.preventDefault();
    this.changeZoom(event.deltaY < 0 ? 0.25 : -0.25);
  }

  startPan(event: PointerEvent): void {
    if (this.zoomLevel <= 1) return;
    event.preventDefault();
    (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
    this.dragStart = {
      x: event.clientX,
      y: event.clientY,
      offsetX: this.zoomOffsetX,
      offsetY: this.zoomOffsetY,
    };
  }

  pan(event: PointerEvent): void {
    if (!this.dragStart) return;
    this.zoomOffsetX = this.dragStart.offsetX + event.clientX - this.dragStart.x;
    this.zoomOffsetY = this.dragStart.offsetY + event.clientY - this.dragStart.y;
  }

  endPan(): void { this.dragStart = null; }

  resetZoom(): void {
    this.zoomLevel = 1;
    this.zoomOffsetX = 0;
    this.zoomOffsetY = 0;
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.zoomImageUrl) this.closeZoom();
  }

  confirm(): void {
    // Waiting matters: confirming mid-upload would save without the photo attached.
    if (this.uploading) return;
    this.ref.close({ confirmed: true, confirmImageUrl: this.confirmUrl ?? undefined });
  }

  close(): void { this.ref.close(); }
}
