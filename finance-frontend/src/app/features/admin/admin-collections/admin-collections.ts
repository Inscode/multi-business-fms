import { Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { CollectionNoteResponse, CollectionNoteService } from '../../../core/services/collection-note';

@Component({
  selector: 'app-admin-collections',
  standalone: true,
  imports: [
    CommonModule, FormsModule, DecimalPipe, DatePipe,
    MatButtonModule, MatIconModule, MatProgressSpinnerModule,
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
  editType: 'CASH' | 'CHEQUE' = 'CASH';

  constructor(private service: CollectionNoteService, private cdr: ChangeDetectorRef) {}

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
    this.service.updateNote(n.id, { amount: this.editAmount, paymentType: this.editType, notes: n.notes }).subscribe({
      next: () => { this.editingId = null; this.load(); },
      error: () => alert('Failed to update.'),
    });
  }

  remove(id: number): void {
    if (!confirm('Delete this collection note? This cannot be undone.')) return;
    this.service.deleteNote(id).subscribe({
      next: () => this.load(),
      error: () => alert('Failed to delete.'),
    });
  }
}
