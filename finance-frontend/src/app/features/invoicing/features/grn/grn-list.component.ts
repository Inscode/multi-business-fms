import { Component, ChangeDetectionStrategy, ChangeDetectorRef, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { catchError, of } from 'rxjs';
import { Grn, GrnLine, GrnService, GrnStatus } from '../../core/services/grn.service';
import { ItemService } from '../../core/services/item.service';
import { CategoryType, Item } from '../../core/models/models';
import { Auth } from '../../../../core/services/auth';

interface DraftLine {
  itemId: number | null;
  qty: number | null;
  /** What the user has typed — the item is only set once one is picked. */
  search: string;
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
    MatSelectModule, MatAutocompleteModule, MatProgressSpinnerModule, MatTooltipModule,
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
  paymentTermsDays: number | null = null;
  openingStock = false;
  notes = '';
  lines: DraftLine[] = [{ itemId: null, qty: null, search: '' }];

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
    this.paymentTermsDays = null;
    this.openingStock = false;
    this.notes = '';
    this.errorMsg = '';
    this.lines = [this.blankLine()];
    this.loadItems();
    this.cdr.markForCheck();
  }

  cancelForm() { this.showForm = false; this.cdr.markForCheck(); }

  /** Category drives the item list — lines are reset so nothing from another category lingers. */
  onCategoryChange() {
    this.lines = [this.blankLine()];
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

  private blankLine(): DraftLine {
    return { itemId: null, qty: null, search: '' };
  }

  addLine() {
    this.lines.push(this.blankLine());
    this.cdr.markForCheck();
  }

  removeLine(i: number) {
    this.lines.splice(i, 1);
    if (this.lines.length === 0) this.addLine();
    this.cdr.markForCheck();
  }

  /**
   * Type-ahead over the category's items — matches on code or description, so
   * "4321" and "umbrella" both find the same row. Capped so a 700-item catalog
   * doesn't render a huge panel on every keystroke.
   */
  filteredItems(line: DraftLine): Item[] {
    const q = (line.search ?? '').toLowerCase().trim();
    const pool = this.availableItems(line);
    if (!q) return pool.slice(0, 50);
    return pool
      .filter(i => i.itemCode.toLowerCase().includes(q)
                || i.description.toLowerCase().includes(q))
      .slice(0, 50);
  }

  /** Typing after a pick invalidates it — the line has no item until one is chosen. */
  onItemInput(line: DraftLine) {
    if (line.itemId != null && line.search !== this.itemLabel(line.itemId)) {
      line.itemId = null;
    }
    this.cdr.markForCheck();
  }

  onItemSelected(line: DraftLine, item: Item) {
    line.itemId = item.id;
    line.search = this.itemLabel(item.id);
    this.cdr.markForCheck();
  }

  /** Price is the catalog's, shown for confirmation only — never typed. */
  unitCostOf(line: DraftLine): number | null {
    const item = this.itemOf(line);
    return item ? this.itemPrice(item) : null;
  }

  /** This note's discount comes from the category. */
  get discountPct(): number {
    return this.categoryDiscounts[this.category] ?? 0;
  }

  netOf(line: DraftLine): number | null {
    const gross = this.lineTotal(line);
    if (gross == null) return null;
    return this.applyDiscount(gross);
  }

  private applyDiscount(gross: number): number {
    return Math.round(gross * (1 - this.discountPct / 100) * 100) / 100;
  }

  get formNetTotal(): number {
    return this.lines.reduce((sum, l) => sum + (this.netOf(l) ?? 0), 0);
  }

  get formDiscountAmount(): number {
    return Math.round((this.formTotal - this.formNetTotal) * 100) / 100;
  }

  itemLabel(id: number | null): string {
    const item = this.items.find(i => i.id === id);
    return item ? `${item.itemCode} — ${item.description}` : '';
  }

  itemPrice(item: Item): number | null {
    return item.wholesalePrice ?? item.wsp ?? null;
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
    const cost = this.unitCostOf(l);
    return l.qty != null && cost != null ? Math.round(l.qty * cost * 100) / 100 : null;
  }

  get formTotal(): number {
    return this.lines.reduce((sum, l) => sum + (this.lineTotal(l) ?? 0), 0);
  }

  get formQty(): number {
    return this.lines.reduce((sum, l) => sum + (l.qty ?? 0), 0);
  }

  /** Mirrors the server's grn_discount_pct_<CATEGORY> settings. */
  categoryDiscounts: Record<string, number> = { RAINCO: 8.54, STATIONERY: 7, PLASTIC: 0 };

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
      paymentTermsDays: this.openingStock ? undefined : (this.paymentTermsDays ?? undefined),
      paymentRequired: !this.openingStock,
      notes: this.notes || undefined,
      lines: this.lines.map(l => ({ itemId: l.itemId!, qty: l.qty! })),
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

  // ── Admin line correction on a pending note ────────────────────

  editingLineId: number | null = null;
  editQty = 0;

  startEditLine(line: GrnLine): void {
    this.editingLineId = line.id;
    this.editQty = line.qty;
    this.cdr.markForCheck();
  }

  cancelEditLine(): void { this.editingLineId = null; this.cdr.markForCheck(); }

  saveLineQty(grn: Grn, line: GrnLine): void {
    if (this.editQty < 1) return;
    this.processing = true;
    this.errorMsg = '';
    this.cdr.markForCheck();
    this.svc.updateLineQty(grn.id, line.id, this.editQty).pipe(catchError(err => {
      this.errorMsg = err?.error?.message ?? 'Could not update that line.';
      return of(null);
    })).subscribe(updated => {
      this.processing = false;
      this.editingLineId = null;
      if (updated) this.replaceGrn(updated);
      this.cdr.markForCheck();
    });
  }

  /** Distinct from removeLine(i), which drops a row from the unsaved form. */
  deleteGrnLine(grn: Grn, line: GrnLine): void {
    this.processing = true;
    this.errorMsg = '';
    this.cdr.markForCheck();
    this.svc.removeLine(grn.id, line.id).pipe(catchError(err => {
      this.errorMsg = err?.error?.message ?? 'Could not remove that line.';
      return of(null);
    })).subscribe(updated => {
      this.processing = false;
      if (updated) this.replaceGrn(updated);
      this.cdr.markForCheck();
    });
  }

  private replaceGrn(updated: Grn): void {
    const i = this.grns.findIndex(g => g.id === updated.id);
    if (i >= 0) this.grns[i] = updated;
    this.grns = [...this.grns];
  }

  /** When this note falls due, previewed as the terms are typed. */
  get dueDatePreview(): string | null {
    if (this.openingStock || !this.paymentTermsDays || !this.receivedDate) return null;
    const d = new Date(this.receivedDate + 'T00:00:00');
    d.setDate(d.getDate() + this.paymentTermsDays);
    return d.toISOString().slice(0, 10);
  }

  togglePaymentRequired(grn: Grn): void {
    this.processing = true;
    this.cdr.markForCheck();
    this.svc.setPaymentRequired(grn.id, !grn.paymentRequired)
      .pipe(catchError(() => of(null)))
      .subscribe(updated => {
        this.processing = false;
        if (updated) this.replaceGrn(updated);
        this.cdr.markForCheck();
      });
  }

  canEditLines(grn: Grn): boolean {
    return this.isAdmin && grn.status === 'PENDING';
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
