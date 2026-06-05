import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTabsModule } from '@angular/material/tabs';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  BillChecklistService,
  ChecklistNoteResponse,
  ChecklistVerifyRow,
} from '../../../core/services/bill-checklist';

interface VerifyRowDraft extends ChecklistVerifyRow {
  draft: number;
}

@Component({
  selector: 'app-bill-checklist-main',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatTabsModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatSelectModule,
    MatProgressSpinnerModule, MatTooltipModule,
  ],
  templateUrl: './bill-checklist-main.html',
  styleUrl: './bill-checklist-main.scss',
})
export class BillChecklistMain implements OnInit {

  itemOptions = ['Plastic', 'Rainco', 'Stationery'];

  // ── Tab 1 ─────────────────────────────────────────────────────────
  addDate   = todayStr();
  addItem   = '';
  addCount: number | null = null;
  addNote   = '';
  adding    = false;
  addError  = '';

  notes: ChecklistNoteResponse[] = [];
  notesDate = todayStr();

  get itemSummary(): { item: string; total: number }[] {
    const map = new Map<string, number>();
    for (const n of this.notes) {
      map.set(n.itemName, (map.get(n.itemName) ?? 0) + n.count);
    }
    return [...map.entries()].map(([item, total]) => ({ item, total }));
  }

  // ── Tab 2 ─────────────────────────────────────────────────────────
  verifyDate    = todayStr();
  verifyRows: VerifyRowDraft[] = [];
  verifyLoading = false;
  saving        = false;
  saveSuccess   = false;

  get totalBalance(): number {
    return this.verifyRows.reduce((s, r) => s + Math.max(0, r.total - r.draft), 0);
  }

  constructor(
    private checklistService: BillChecklistService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void { this.loadNotes(); }

  onTabChange(index: number): void {
    if (index === 0) this.loadNotes();
    if (index === 1) this.loadVerify();
  }

  // ── Add Notes ─────────────────────────────────────────────────────

  loadNotes(): void {
    this.checklistService.getNotesForDate(this.notesDate).subscribe({
      next: (list) => { this.notes = list; this.cdr.markForCheck(); },
      error: () => {},
    });
  }

  submitNote(): void {
    if (!this.addItem || !this.addCount || this.addCount <= 0) return;
    this.adding   = true;
    this.addError = '';
    this.cdr.markForCheck();

    this.checklistService.addNote({
      itemName: this.addItem,
      count:    this.addCount,
      note:     this.addNote.trim() || undefined,
      entryDate: this.addDate,
    }).subscribe({
      next: () => {
        this.adding   = false;
        this.addItem  = '';
        this.addCount = null;
        this.addNote  = '';
        this.cdr.markForCheck();
        if (this.notesDate === this.addDate) this.loadNotes();
      },
      error: (err) => {
        this.adding   = false;
        this.addError = err?.error?.message ?? 'Failed to add.';
        this.cdr.markForCheck();
      },
    });
  }

  deleteNote(id: number): void {
    if (!confirm('Delete this entry?')) return;
    this.checklistService.deleteNote(id).subscribe({
      next: () => this.loadNotes(),
      error: () => alert('Failed to delete.'),
    });
  }

  // ── Verify & Close ────────────────────────────────────────────────

  loadVerify(): void {
    this.verifyLoading = true;
    this.saveSuccess   = false;
    this.cdr.markForCheck();

    this.checklistService.getVerifyView(this.verifyDate).subscribe({
      next: (rows) => {
        this.verifyRows    = rows.map(r => ({ ...r, draft: r.markedEntered }));
        this.verifyLoading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.verifyLoading = false;
        this.cdr.markForCheck();
      },
    });
  }

  rowBalance(row: VerifyRowDraft): number {
    return Math.max(0, row.total - (row.draft ?? 0));
  }

  saveClosing(): void {
    this.saving     = true;
    this.saveSuccess = false;
    this.cdr.markForCheck();

    const items = this.verifyRows.map(r => ({
      itemName:      r.itemName,
      markedEntered: Math.min(r.draft ?? 0, r.total),
    }));

    this.checklistService.saveClosing({ closingDate: this.verifyDate, items }).subscribe({
      next: () => {
        this.saving      = false;
        this.saveSuccess = true;
        this.cdr.markForCheck();
        this.loadVerify();
      },
      error: () => {
        this.saving = false;
        this.cdr.markForCheck();
      },
    });
  }
}

function todayStr(): string {
  return new Date().toISOString().split('T')[0];
}
