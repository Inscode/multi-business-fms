import { Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { CollectionNoteResponse, CollectionNoteService } from '../../../core/services/collection-note';
import { ConfirmDialog } from '../../../shared/confirm-dialog/confirm-dialog';

@Component({
  selector: 'app-admin-collections',
  standalone: true,
  imports: [
    CommonModule, FormsModule, DecimalPipe, DatePipe,
    MatButtonModule, MatButtonToggleModule, MatIconModule, MatProgressSpinnerModule, MatDialogModule,
  ],
  templateUrl: './admin-collections.html',
  styleUrl: './admin-collections.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminCollections implements OnInit {
  notes: CollectionNoteResponse[] = [];
  loading = false;
  editingId: number | null = null;
  editAmount = 0;
  editType: 'CASH' | 'CHEQUE' | 'BANK_TRANSFER' = 'CASH';
  statusFilter: 'ALL' | 'PENDING' | 'MATCHED' = 'ALL';
  errorMsg = '';

  get filtered(): CollectionNoteResponse[] {
    if (this.statusFilter === 'ALL') return this.notes;
    return this.notes.filter(n => n.status === this.statusFilter);
  }

  get pendingCount(): number  { return this.notes.filter(n => n.status === 'PENDING').length; }
  get matchedCount(): number  { return this.notes.filter(n => n.status === 'MATCHED').length; }

  constructor(
    private service: CollectionNoteService,
    private dialog: MatDialog,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.service.getAllNotes().subscribe({
      next: (n) => { this.notes = n; this.loading = false; this.cdr.detectChanges(); },
      error: () => { this.loading = false; this.cdr.detectChanges(); },
    });
  }

  startEdit(n: CollectionNoteResponse): void {
    this.editingId = n.id;
    this.editAmount = n.amount;
    this.editType = n.paymentType;
    this.cdr.detectChanges();
  }

  cancelEdit(): void { this.editingId = null; this.cdr.detectChanges(); }

  saveEdit(n: CollectionNoteResponse): void {
    this.service.updateNote(n.id, { amount: this.editAmount, paymentType: this.editType as 'CASH' | 'CHEQUE' | 'BANK_TRANSFER', notes: n.notes }).subscribe({
      next: () => { this.editingId = null; this.load(); },
      error: () => alert('Failed to update.'),
    });
  }

  /**
   * A matched note already has a confirmed payment behind it (admin cash collections
   * self-confirm), so deleting also reverses that payment and restores the bill balance.
   * The dialog says so plainly — this is not just removing a note.
   */
  remove(n: CollectionNoteResponse): void {
    const matched = n.status === 'MATCHED';
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Delete Collection',
        message: matched
          ? `Delete the Rs ${n.amount.toLocaleString()} ${n.paymentType} collection from ${n.customerName} (${n.billNumber})? `
            + `The payment already recorded against this bill will be reversed and the balance restored.`
          : `Delete the Rs ${n.amount.toLocaleString()} ${n.paymentType} collection from ${n.customerName} (${n.billNumber})? This cannot be undone.`,
        confirmText: 'Delete',
        confirmColor: 'warn',
      },
      maxWidth: '95vw',
    }).afterClosed().subscribe(result => {
      if (!result?.confirmed) return;
      this.errorMsg = '';
      this.service.deleteNote(n.id).subscribe({
        next: () => this.load(),
        error: (e) => {
          this.errorMsg = e?.error?.message ?? 'Failed to delete this collection.';
          this.cdr.detectChanges();
        },
      });
    });
  }
}
