import { Component, ChangeDetectionStrategy, ChangeDetectorRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { ContributionMatrixDialog } from './contribution-matrix/contribution-matrix.dialog';
import { catchError, of } from 'rxjs';
import { environment } from '../../../../../environments/environment';
import { CustomerService } from '../../core/services/customer.service';
import { Auth } from '../../../../core/services/auth';
import { Customer } from '../../core/models/models';

interface ImportedLine {
  itemCode?: string;
  description?: string;
  qty?: number;
  /** Free quantity as printed on the agent's sheet — read, not typed. */
  freeQty?: number;
  unitPrice?: number;
  lineTotal?: number;
}

interface ParsedInvoice {
  invoiceNo?: string;
  /** The number this will carry in both the invoicing and bills sections. */
  billNumber?: string | null;
  /** Customer the parser resolved — null when the printed name matched nobody. */
  customerId?: number | null;
  customerName?: string | null;
  /** The name printed on the agent's invoice. Often not the real buyer. */
  billedName?: string | null;
  invoiceDate?: string;
  netTotal?: number;
  lines: ImportedLine[];
  /** Codes that could not be pinned to exactly one catalog item. */
  unmatchedCodes?: string[];
  /** True when this invoice will be refused — an unmatched item or unresolved customer. */
  blocked?: boolean;
  blockReason?: string;
  /** Already loaded by an earlier run — importing again would be a duplicate. */
  alreadyImported?: boolean;

  // ── Client-side, set by the customer picker ──
  /** Text in the picker; also the display value for the chosen customer. */
  customerSearch?: string;
  /** Customer this will actually be imported against. */
  chosenId?: number | null;
  /** Optional free umbrella (K01047) given against this invoice. */
  freeUmbrella?: number | null;
  /** Dropped from this load — not imported, not counted in the totals. */
  excluded?: boolean;
  /** The number this will carry, editable by an admin. Never allowed to be empty. */
  numberEdit?: string;
}

interface PreviewResponse {
  fileCount: number;
  invoiceCount: number;
  blockedCount: number;
  duplicateCount: number;
  importableCount: number;
  warnings: string[];
  invoices: ParsedInvoice[];
}

interface ImportResponse {
  batchId: number;
  imported: number;
  blocked: number;
  /** Already in the system — left untouched, not an error. */
  skipped: number;
  skippedRefs: string[];
  /** Attached to bills already entered by hand: stock moved, no second bill. */
  stockOnly: number;
  stockOnlyRefs: string[];
  warnings: string[];
  errors: string[];
}

type ImportCategory = 'RAINCO' | 'STATIONERY';

@Component({
  selector: 'app-import',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, RouterLink,
            MatButtonModule, MatIconModule, MatProgressSpinnerModule,
            MatSelectModule, MatFormFieldModule, MatTooltipModule,
            MatInputModule, MatAutocompleteModule, MatDialogModule],
  templateUrl: './import.component.html',
  styleUrl: './import.component.scss'
})
export class ImportComponent implements OnInit {
  private http = inject(HttpClient);
  private cdr  = inject(ChangeDetectorRef);
  private customerService = inject(CustomerService);
  private auth = inject(Auth);
  private dialog = inject(MatDialog);

  get isAdmin(): boolean { return this.auth.getRole() === 'ADMIN'; }

  customers: Customer[] = [];

  selectedCategory: ImportCategory | null = null;
  /** One summary load often arrives as several files, previewed and totalled together. */
  selectedFiles: File[] = [];
  dragOver    = false;
  parsing     = false;
  importing   = false;
  parsed:     ParsedInvoice[] = [];
  parseWarnings: string[] = [];
  importResult: ImportResponse | null = null;
  error       = '';

  /**
   * The batch these uploads are accumulating into. Several files often make up one
   * agent summary bill, so the totals need to span all of them.
   */
  batchId: number | null = null;
  batchFiles: string[] = [];

  ngOnInit() {
    this.customerService.list().subscribe({
      next: list => { this.customers = list; this.cdr.markForCheck(); },
      error: () => {},
    });
  }

  chooseCategory(category: ImportCategory) {
    this.selectedCategory = category;
    this.cdr.markForCheck();
  }

  changeCategory() {
    this.selectedCategory = null;
    // A batch cannot mix categories, so switching ends it.
    this.batchId = null;
    this.batchFiles = [];
    this.reset();
  }

  /** Keep the batch, clear the file — the next upload joins the same summary. */
  importAnotherIntoBatch() {
    this.reset();
  }

  /** Finish the batch; the next upload starts a fresh one. */
  startNewBatch() {
    this.batchId = null;
    this.batchFiles = [];
    this.reset();
  }

  onDragOver(e: DragEvent) { e.preventDefault(); this.dragOver = true; }
  onDragLeave()             { this.dragOver = false; }

  onDrop(e: DragEvent) {
    e.preventDefault();
    this.dragOver = false;
    this.addFiles(Array.from(e.dataTransfer?.files ?? []));
  }

  onFileChange(e: Event) {
    const input = e.target as HTMLInputElement;
    this.addFiles(Array.from(input.files ?? []));
    // Let the same file be chosen again after removing it.
    input.value = '';
  }

  /** Files accumulate, so a second pick adds to the load rather than replacing it. */
  addFiles(files: File[]) {
    const valid = files.filter(f => /\.(xls|xlsx)$/i.test(f.name));
    if (valid.length !== files.length) {
      this.error = 'Only Excel files (.xls / .xlsx) can be imported — others were ignored.';
    } else {
      this.error = '';
    }
    for (const f of valid) {
      if (!this.selectedFiles.some(x => x.name === f.name && x.size === f.size)) {
        this.selectedFiles.push(f);
      }
    }
    if (this.selectedFiles.length === 0) { this.cdr.markForCheck(); return; }

    this.parsed = [];
    this.parseWarnings = [];
    this.importResult = null;
    this.cdr.markForCheck();
    this.parse();
  }

  removeFile(idx: number) {
    this.selectedFiles.splice(idx, 1);
    this.parsed = [];
    this.parseWarnings = [];
    this.cdr.markForCheck();
    if (this.selectedFiles.length) this.parse();
  }

  parse() {
    if (!this.selectedFiles.length || !this.selectedCategory) return;
    this.parsing = true;
    const fd = new FormData();
    for (const f of this.selectedFiles) fd.append('files', f);
    fd.append('category', this.selectedCategory);
    this.http.post<PreviewResponse>(`${environment.apiUrl}/invoicing/import/preview`, fd)
      .pipe(catchError(err => {
        this.error   = err?.error?.message ?? 'Failed to parse file';
        this.parsing = false;
        this.cdr.markForCheck();
        return of(null);
      }))
      .subscribe(res => {
        this.parsed        = (res?.invoices ?? []).map(inv => ({
          ...inv,
          chosenId: inv.customerId ?? null,
          customerSearch: inv.customerName ?? '',
          numberEdit: inv.billNumber ?? '',
        }));
        this.parseWarnings = res?.warnings ?? [];
        this.parsing = false;
        this.cdr.markForCheck();
      });
  }

  importAll() {
    if (!this.selectedFiles.length || !this.selectedCategory) return;
    this.importing = true;
    const fd = new FormData();
    for (const f of this.selectedFiles) fd.append('files', f);
    fd.append('category', this.selectedCategory);
    fd.append('customerOverrides', JSON.stringify(this.customerOverrides()));
    fd.append('freeUmbrellas', JSON.stringify(this.freeUmbrellas()));
    fd.append('excludeRefs', JSON.stringify(this.excludedRefs()));
    fd.append('numberOverrides', JSON.stringify(this.numberOverrides()));
    if (this.batchId) fd.append('batchId', String(this.batchId));
    this.http.post<ImportResponse>(`${environment.apiUrl}/invoicing/import/confirm`, fd)
      .pipe(catchError(err => {
        this.error     = err?.error?.message ?? 'Import failed';
        this.importing = false;
        this.cdr.markForCheck();
        return of(null);
      }))
      .subscribe(res => {
        this.importing    = false;
        this.importResult = res;
        if (res?.batchId) {
          this.batchId = res.batchId;
          this.batchFiles.push(...this.selectedFiles.map(f => f.name));
        }
        this.cdr.markForCheck();
      });
  }

  reset() {
    this.selectedFiles = [];
    this.parsed       = [];
    this.parseWarnings = [];
    this.importResult = null;
    this.error        = '';
    this.cdr.markForCheck();
  }

  // ── Customer picker ────────────────────────────────────────────────
  // Invoices are often raised under a name that isn't the real buyer's, so the
  // customer is chosen here rather than being fixed by whatever the sheet said.

  /** Bound as a field — mat-autocomplete calls this detached from the component. */
  displayCustomer = (c: Customer | string | null): string =>
    !c ? '' : (typeof c === 'string' ? c : c.name);

  filteredCustomers(inv: ParsedInvoice): Customer[] {
    const q = (inv.customerSearch ?? '').trim().toLowerCase();
    const pool = q
      ? this.customers.filter(c => c.name.toLowerCase().includes(q))
      : this.customers;
    return pool.slice(0, 30);
  }

  onCustomerInput(inv: ParsedInvoice, text: string) {
    inv.customerSearch = text;
    // Free text is not a customer — clear the selection until one is actually picked,
    // so a half-typed name can never import against the previously chosen customer.
    inv.chosenId = null;
    this.cdr.markForCheck();
  }

  pickCustomer(inv: ParsedInvoice, c: Customer) {
    inv.chosenId = c.id;
    inv.customerSearch = c.name;
    this.cdr.markForCheck();
  }

  clearCustomer(inv: ParsedInvoice) {
    inv.chosenId = null;
    inv.customerSearch = '';
    this.cdr.markForCheck();
  }

  /** True once the accountant has pointed this somewhere other than the parsed customer. */
  isRedirected(inv: ParsedInvoice): boolean {
    return inv.chosenId != null && inv.chosenId !== (inv.customerId ?? null);
  }

  chosenName(inv: ParsedInvoice): string {
    return this.customers.find(c => c.id === inv.chosenId)?.name ?? '';
  }

  /** Only stationery invoices carry the free umbrella. */
  get isStationery(): boolean { return this.selectedCategory === 'STATIONERY'; }

  /** externalRef → free umbrella qty, for the confirm call. */
  private freeUmbrellas(): Record<string, number> {
    const map: Record<string, number> = {};
    for (const inv of this.kept()) {
      const q = Number(inv.freeUmbrella ?? 0);
      if (inv.invoiceNo && q > 0) map[inv.invoiceNo] = q;
    }
    return map;
  }

  /** Free quantities the sheet already carries — nothing to type for these. */
  sheetFreeQty(inv: ParsedInvoice): number {
    return (inv.lines ?? []).reduce((s, l) => s + (l.freeQty ?? 0), 0);
  }

  /** externalRef → chosen customer id, for the confirm call. */
  private customerOverrides(): Record<string, number> {
    const map: Record<string, number> = {};
    for (const inv of this.kept()) {
      if (inv.invoiceNo && inv.chosenId != null) map[inv.invoiceNo] = inv.chosenId;
    }
    return map;
  }

  // ── Dropping invoices from the load ─────────────────────────────────
  // A summary bill often covers only some of what is in the files, so unwanted
  // invoices are dropped here rather than imported and cleaned up afterwards.

  toggleExcluded(inv: ParsedInvoice) {
    inv.excluded = !inv.excluded;
    this.cdr.markForCheck();
  }

  includeAllInvoices() {
    for (const inv of this.parsed) inv.excluded = false;
    this.cdr.markForCheck();
  }

  excludedCount(): number { return this.parsed.filter(i => i.excluded).length; }

  /**
   * Numbers an admin changed. Only the differences are sent — an untouched number is
   * left for the server to derive, so the two can never disagree.
   */
  private numberOverrides(): Record<string, string> {
    const map: Record<string, string> = {};
    for (const inv of this.kept()) {
      const edited = (inv.numberEdit ?? '').trim();
      if (inv.invoiceNo && edited && edited !== (inv.billNumber ?? '')) {
        map[inv.invoiceNo] = edited;
      }
    }
    return map;
  }

  /** An invoice with no number cannot be imported — it is also the bill number. */
  missingNumber(inv: ParsedInvoice): boolean {
    return !(inv.numberEdit ?? '').trim();
  }

  missingNumberCount(): number {
    return this.kept().filter(i => !i.blocked && !i.alreadyImported && this.missingNumber(i)).length;
  }

  onNumberChanged() { this.cdr.markForCheck(); }

  /** Invoice numbers to leave out, for the confirm call. */
  private excludedRefs(): string[] {
    return this.parsed.filter(i => i.excluded && i.invoiceNo).map(i => i.invoiceNo!);
  }

  /** Everything still in the load — the basis for both the totals and the import. */
  private kept(): ParsedInvoice[] {
    return this.parsed.filter(i => !i.excluded);
  }

  /** Invoices that will actually import — blocked and duplicate ones are refused. */
  importable(): ParsedInvoice[] {
    return this.parsed.filter(i => !i.excluded && !i.blocked && !i.alreadyImported
                                   && i.chosenId != null && !this.missingNumber(i));
  }

  /** Not blocked, but still waiting on someone to say who the customer is. */
  needsCustomer(): ParsedInvoice[] {
    return this.parsed.filter(i => !i.excluded && !i.blocked && !i.alreadyImported
                                   && i.chosenId == null);
  }

  duplicateInvoices(): ParsedInvoice[] {
    return this.parsed.filter(i => !i.excluded && i.alreadyImported);
  }

  /** Nothing left to do: the whole file is already in the system. */
  allDuplicates(): boolean {
    return this.parsed.length > 0 && this.duplicateInvoices().length === this.parsed.length;
  }

  blockedInvoices(): ParsedInvoice[] {
    return this.parsed.filter(i => !i.excluded && i.blocked && !i.alreadyImported);
  }

  isUnmatched(inv: ParsedInvoice, code?: string): boolean {
    return !!code && !!inv.unmatchedCodes?.includes(code);
  }

  totalLines() { return this.parsed.reduce((s, p) => s + (p.lines?.length ?? 0), 0); }

  totalFileSizeKb(): number {
    return this.selectedFiles.reduce((s, f) => s + f.size, 0) / 1024;
  }

  // ── Product totals, before anything is saved ────────────────────────
  // Covers every invoice in the chosen files, matching what the agent's summary bill
  // covers — including ones that are blocked or already imported, since the agent
  // counted those too. The counts above say how many will actually import.

  productSummary(): { code: string; description: string; qty: number; freeQty: number; value: number }[] {
    const map = new Map<string, { code: string; description: string; qty: number; freeQty: number; value: number }>();
    for (const inv of this.kept()) {
      for (const l of inv.lines ?? []) {
        const code = l.itemCode || '—';
        const row = map.get(code) ?? { code, description: l.description ?? '', qty: 0, freeQty: 0, value: 0 };
        row.qty     += l.qty ?? 0;
        row.freeQty += l.freeQty ?? 0;
        row.value   += l.lineTotal ?? 0;
        map.set(code, row);
      }
    }
    return [...map.values()].sort((a, b) => a.code.localeCompare(b.code));
  }

  summaryTotalQty()   { return this.productSummary().reduce((s, p) => s + p.qty, 0); }
  summaryTotalFree()  { return this.productSummary().reduce((s, p) => s + p.freeQty, 0); }
  summaryTotalValue() { return this.productSummary().reduce((s, p) => s + p.value, 0); }

  showSummary = true;
  toggleSummary() { this.showSummary = !this.showSummary; this.cdr.markForCheck(); }

  /**
   * The same load as a grid: items down the side, bills across the top.
   *
   * <p>The totals table above says whether the load is right in aggregate; this says
   * which bill a discrepancy came from, which is the question actually being asked
   * when the agent's summary does not match.
   */
  openContributionMatrix(): void {
    const invoices = this.kept()
      .filter(i => !!i.invoiceNo)
      .map(i => ({
        invoiceNo: i.invoiceNo,
        // Whoever it will actually be billed to, which is not always the printed name.
        customerName: i.customerSearch || i.customerName || i.billedName || '',
        lines: i.lines ?? [],
      }));

    this.dialog.open(ContributionMatrixDialog, {
      data: {
        companyName: 'Ghanim Distributors',
        scope: this.matrixScope(),
        invoices,
        includeFree: false,
      },
      panelClass: 'matrix-dialog-panel',
      // Sized to the grid rather than to the screen: a fixed width leaves a wide
      // empty margin whenever the load has few bills.
      maxWidth: '96vw',
      autoFocus: false,
    });
  }

  /** Names the load on the printed sheet — dates covered, and how many bills. */
  private matrixScope(): string {
    const kept = this.kept();
    const dates = [...new Set(kept.map(i => i.invoiceDate).filter(Boolean))].sort();
    const span = dates.length === 0 ? ''
      : dates.length === 1 ? this.shortDate(dates[0]!)
      : `${this.shortDate(dates[0]!)} – ${this.shortDate(dates[dates.length - 1]!)}`;
    const cat = this.selectedCategory ? this.selectedCategory : '';
    return [cat, span, `${kept.length} bills`].filter(Boolean).join(', ');
  }

  private shortDate(iso: string): string {
    const d = new Date(iso);
    return isNaN(d.getTime()) ? iso
      : d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
  }
}
