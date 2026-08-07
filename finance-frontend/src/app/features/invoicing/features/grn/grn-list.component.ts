import { Component, ChangeDetectionStrategy, ChangeDetectorRef, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { catchError, of } from 'rxjs';
import { Grn, GrnService, GrnStatus } from '../../core/services/grn.service';
import { ItemService } from '../../core/services/item.service';
import { CategoryType, Item } from '../../core/models/models';
import { Auth } from '../../../../core/services/auth';

interface DraftLine {
  itemId: number | null;
  qty: number | null;
  unitCost: number | null;
}

/**
 * Goods received notes. Accountants and admins enter what arrived; only an admin
 * approves, and stock moves at that point. Picking the category drives which items
 * (with their brand and price) can be added to the note.
 */
@Component({
  selector: 'app-grn-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule, FormsModule,
    MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatProgressSpinnerModule, MatTooltipModule,
  ],
  templateUrl: './grn-list.component.html',
  styleUrl: './grn-list.component.scss',
})
export class GrnListComponent implements OnInit {
  private svc = inject(GrnService);
  private itemSvc = inject(ItemService);
  private auth = inject(Auth);
  cdr = inject(ChangeDetectorRef);

  grns: Grn[] = [];
  loading = true;
  tab: 'ALL' | GrnStatus = 'PENDING';

  rejectingId: number | null = null;
  rejectReason = '';
  processing = false;
  errorMsg = '';

  // ── Create form ────────────────────────────────────────────────
  showForm = false;
  saving = false;
  categories: CategoryType[] = ['RAINCO', 'STATIONERY', 'PLASTIC'];
  category: CategoryType = 'RAINCO';
  items: Item[] = [];
  itemsLoading = false;
  supplierName = '';
  receivedDate = new Date().toISOString().substring(0, 10);
  notes = '';
  lines: DraftLine[] = [{ itemId: null, qty: null, unitCost: null }];

  get isAdmin(): boolean { return this.auth.getRole() === 'ADMIN'; }

  ngOnInit() { this.load(); }

  get filtered(): Grn[] {
    return this.tab === 'ALL' ? this.grns : this.grns.filter(g => g.status === this.tab);
  }

  load() {
    this.loading = true;
    this.svc.list().pipe(catchError(() => of([]))).subscribe(data => {
      this.grns = data;
      this.loading = false;
      this.cdr.markForCheck();
    });
  }

  setTab(t: 'ALL' | GrnStatus) { this.tab = t; this.cdr.markForCheck(); }

  countByStatus(s: GrnStatus): number {
    return this.grns.filter(g => g.status === s).length;
  }

  // ── Create form ────────────────────────────────────────────────

  openNew() {
    this.showForm = true;
    this.category = 'RAINCO';
    this.supplierName = '';
    this.receivedDate = new Date().toISOString().substring(0, 10);
    this.notes = '';
    this.errorMsg = '';
    this.lines = [{ itemId: null, qty: null, unitCost: null }];
    this.loadItems();
    this.cdr.markForCheck();
  }

  cancelForm() { this.showForm = false; this.cdr.markForCheck(); }

  /** Category drives the item list — lines are reset so nothing from another category lingers. */
  onCategoryChange() {
    this.lines = [{ itemId: null, qty: null, unitCost: null }];
    this.loadItems();
  }

  private loadItems() {
    this.itemsLoading = true;
    this.cdr.markForCheck();
    this.itemSvc.list(this.category).pipe(catchError(() => of([]))).subscribe(data => {
      this.items = data.filter(i => i.active);
      this.itemsLoading = false;
      this.cdr.markForCheck();
    });
  }

  addLine() {
    this.lines.push({ itemId: null, qty: null, unitCost: null });
    this.cdr.markForCheck();
  }

  removeLine(i: number) {
    this.lines.splice(i, 1);
    if (this.lines.length === 0) this.addLine();
    this.cdr.markForCheck();
  }

  /** Default the cost to the item's own price so it rarely needs typing. */
  onItemChange(line: DraftLine) {
    const item = this.items.find(i => i.id === line.itemId);
    if (item && line.unitCost == null) {
      line.unitCost = item.wholesalePrice ?? item.wsp ?? null;
    }
    this.cdr.markForCheck();
  }

  itemOf(line: DraftLine): Item | undefined {
    return this.items.find(i => i.id === line.itemId);
  }

  /** Items not already on another line — stops the same item being received twice. */
  availableItems(line: DraftLine): Item[] {
    const taken = new Set(this.lines.filter(l => l !== line && l.itemId != null).map(l => l.itemId));
    return this.items.filter(i => !taken.has(i.id));
  }

  lineTotal(l: DraftLine): number | null {
    return l.qty != null && l.unitCost != null ? l.qty * l.unitCost : null;
  }

  get formTotal(): number {
    return this.lines.reduce((sum, l) => sum + (this.lineTotal(l) ?? 0), 0);
  }

  get formQty(): number {
    return this.lines.reduce((sum, l) => sum + (l.qty ?? 0), 0);
  }

  get formValid(): boolean {
    return !!this.receivedDate &&
      this.lines.length > 0 &&
      this.lines.every(l => l.itemId != null && l.qty != null && l.qty > 0);
  }

  submit() {
    if (!this.formValid) return;
    this.saving = true;
    this.errorMsg = '';

    this.svc.create({
      category: this.category,
      supplierName: this.supplierName || undefined,
      receivedDate: this.receivedDate,
      notes: this.notes || undefined,
      lines: this.lines.map(l => ({
        itemId: l.itemId!,
        qty: l.qty!,
        unitCost: l.unitCost ?? undefined,
      })),
    }).pipe(catchError(err => {
      this.errorMsg = err?.error?.message ?? 'Could not submit this GRN.';
      return of(null);
    })).subscribe(res => {
      this.saving = false;
      if (res) { this.showForm = false; this.load(); }
      this.cdr.markForCheck();
    });
  }

  // ── Approve / reject (admin only) ──────────────────────────────

  approve(g: Grn) {
    this.processing = true;
    this.errorMsg = '';
    this.cdr.markForCheck();
    this.svc.approve(g.id).pipe(catchError(err => {
      this.errorMsg = err?.error?.message ?? 'Could not approve this GRN.';
      return of(null);
    })).subscribe(res => {
      this.processing = false;
      if (res) this.load(); else this.cdr.markForCheck();
    });
  }

  startReject(id: number) { this.rejectingId = id; this.rejectReason = ''; this.cdr.markForCheck(); }
  cancelReject() { this.rejectingId = null; this.cdr.markForCheck(); }

  confirmReject() {
    if (!this.rejectingId || !this.rejectReason.trim()) return;
    this.processing = true;
    this.svc.reject(this.rejectingId, this.rejectReason)
      .pipe(catchError(() => of(null)))
      .subscribe(res => {
        this.processing = false;
        this.rejectingId = null;
        if (res) this.load(); else this.cdr.markForCheck();
      });
  }

  // ── Row expansion ──────────────────────────────────────────────

  expandedId: number | null = null;
  toggleLines(id: number) {
    this.expandedId = this.expandedId === id ? null : id;
    this.cdr.markForCheck();
  }

  statusClass(s: GrnStatus): string {
    return ({ PENDING: 'warn', APPROVED: 'success', REJECTED: 'danger' } as Record<string, string>)[s] ?? 'info';
  }

  categoryClass(c: string): string {
    return ({ RAINCO: 'success', STATIONERY: 'warn', PLASTIC: 'info' } as Record<string, string>)[c] ?? 'info';
  }
}
