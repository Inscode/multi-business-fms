import { Component, ChangeDetectionStrategy, ChangeDetectorRef, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { catchError, of } from 'rxjs';
import { InvoiceService } from '../../../core/services/invoice.service';
import { ItemService } from '../../../core/services/item.service';
import { CustomerService } from '../../../core/services/customer.service';
import { Item, Customer, InvoiceMethod, InvoiceType, Quote } from '../../../core/models/models';
import { Bill, BillNumberOption } from '../../../../../core/services/bill';

interface LineEntry {
  item: Item;
  qty: number;
  /** Given free — no value, but it still leaves the warehouse. */
  freeQty: number;
  /** Set once someone edits the free figure, so the scheme stops overwriting it. */
  freeTouched?: boolean;
}

@Component({
  selector: 'app-invoice-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, ReactiveFormsModule,
            MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
            MatSelectModule, MatProgressSpinnerModule, MatTooltipModule, MatAutocompleteModule],
  templateUrl: './invoice-form.component.html',
  styleUrl: './invoice-form.component.scss'
})
export class InvoiceFormComponent implements OnInit {
  private billApi = inject(Bill);

  suggestedNumbers: BillNumberOption[] = [];
  loadingNumbers = false;

  /** Live pricing of the draft — the server runs the same engine it will use on save. */
  quote: Quote | null = null;
  quoting = false;
  private quoteSeq = 0;
  private quoteTimer: ReturnType<typeof setTimeout> | null = null;

  private route = inject(ActivatedRoute);
  /** Set when the admin opened an existing invoice to rewrite it. */
  editingId: number | null = null;

  private svc      = inject(InvoiceService);
  private itemSvc  = inject(ItemService);
  private custSvc  = inject(CustomerService);
  private router   = inject(Router);
  cdr      = inject(ChangeDetectorRef);
  private fb       = inject(FormBuilder);

  items:     Item[]     = [];
  customers: Customer[] = [];
  lines:     LineEntry[] = [];
  loading    = false;
  saving     = false;
  error      = '';

  itemSearch    = '';
  filteredItems: Item[] = [];

  customerSearch     = '';
  filteredCustomers: Customer[] = [];

  today = new Date().toISOString().slice(0, 10);

  form = this.fb.group({
    method:              ['MIX' as InvoiceMethod, Validators.required],
    invoiceType:         ['CREDIT' as InvoiceType, Validators.required],
    customerId:          [null as number | null,   Validators.required],
    invoiceDate:         [this.today,              Validators.required],
    externalRef:         [''],
    billSource:          ['SYSTEM', Validators.required],
    billNumber:          ['', Validators.required],
    agentPrintedNet:     [null as number | null],
    plasticDiscountPct:  [null as number | null],
    plasticDiscountAmount: [null as number | null]
  });

  ngOnInit() {
    this.loading = true;
    const idParam = this.route.snapshot.paramMap.get('id');
    this.editingId = idParam ? Number(idParam) : null;

    Promise.all([
      this.itemSvc.list().pipe(catchError(() => of([]))).toPromise(),
      this.custSvc.list().pipe(catchError(() => of([]))).toPromise()
    ]).then(([items, customers]) => {
      this.items     = items     ?? [];
      this.customers = customers ?? [];
      this.filteredItems = this.itemsForMethod();
      this.filteredCustomers = this.customers;
      this.loading = false;
      if (this.editingId) this.loadForEdit(this.editingId);
      this.cdr.markForCheck();
    });

    this.form.get('invoiceType')!.valueChanges.subscribe(() => this.refreshQuote());
    this.form.get('plasticDiscountPct')!.valueChanges.subscribe(() => this.refreshQuote());
    this.form.get('plasticDiscountAmount')!.valueChanges.subscribe(() => this.refreshQuote());

    this.form.get('billSource')!.valueChanges.subscribe(() => {
      this.form.get('billNumber')!.setValue('');
      this.loadNumbers();
    });
    this.loadNumbers();

    this.form.get('method')!.valueChanges.subscribe(() => {
      // A method change can make a free quantity illegal; clear it rather than let
      // the save fail on a field that is no longer even visible.
      this.lines.forEach(l => {
        if (!this.allowsFreeOn(l)) { l.freeQty = 0; l.freeTouched = false; }
      });
      // Business changes with the method, and each business has its own number run.
      if (!this.billSources.includes(this.form.value.billSource!)) {
        this.form.get('billSource')!.setValue('SYSTEM', { emitEvent: false });
      }
      this.form.get('billNumber')!.setValue('');
      this.loadNumbers();
      this.lines = this.lines.filter(l => this.itemAllowed(l.item));
      this.itemSearch = '';
      this.filteredItems = this.itemsForMethod();
      this.cdr.markForCheck();
    });
  }

  /**
   * Loads an existing invoice into the form. The number and method stay fixed: the
   * number is also the bill number, and the method decides which business that bill
   * belongs to — changing either would leave the two records pointing at each other
   * with different identities.
   */
  private loadForEdit(id: number) {
    this.svc.getById(id).pipe(catchError(() => of(null))).subscribe(inv => {
      if (!inv) { this.error = 'Could not load the invoice'; this.cdr.markForCheck(); return; }
      this.form.patchValue({
        method: inv.method,
        invoiceType: inv.invoiceType,
        customerId: inv.customerId,
        invoiceDate: String(inv.invoiceDate).slice(0, 10),
        externalRef: inv.externalRef ?? '',
        agentPrintedNet: inv.agentPrintedNet ?? null,
        plasticDiscountPct: inv.plasticDiscountPct ?? null,
        plasticDiscountAmount: inv.plasticDiscountAmount ?? null,
      }, { emitEvent: false });
      this.form.get('method')!.disable({ emitEvent: false });
      this.form.get('billSource')!.disable({ emitEvent: false });
      this.form.get('billNumber')!.disable({ emitEvent: false });

      this.customerSearch = inv.customerName ?? '';
      this.lines = (inv.lines ?? []).map(l => ({
        item: this.items.find(i => i.id === l.itemId)
              ?? ({ id: l.itemId, itemCode: l.itemCode, description: l.itemDescription } as Item),
        qty: l.qty,
        freeQty: l.freeQty ?? 0,
      }));
      // Price what was loaded, so an edit shows its discount straight away.
      this.refreshQuote();
      this.cdr.markForCheck();
    });
  }

  get isEditing(): boolean { return this.editingId != null; }

  /** The number can't change on an edit — it also identifies the bill. */
  fixedNumber(): string { return this.editingInvoiceNo; }
  private editingInvoiceNo = '';

  /**
   * The item's own buy-N-get-M scheme, e.g. shoe polish at 12 → 3. Integer division:
   * 25 gives 6 free, not 6.25.
   */
  /**
   * The umbrella thrown in on a stationery bill. It is a Rainco-category item, so it
   * has to be named explicitly — every category rule would otherwise refuse it.
   */
  private static readonly FREE_UMBRELLA_CODES = ['RC-K01047', 'K01047'];
  /** Shoe polish is the only stationery line that ever carries a free quantity. */
  private static readonly POLISH_BRAND = 'shoe polish';

  isFreeUmbrella(item: Item): boolean {
    const code = (item.itemCode ?? '').trim().toUpperCase();
    return InvoiceFormComponent.FREE_UMBRELLA_CODES.includes(code);
  }

  isPolishItem(item: Item): boolean {
    return (item.brandName ?? '').trim().toLowerCase()
      .includes(InvoiceFormComponent.POLISH_BRAND);
  }

  /**
   * Free issue is a stationery arrangement, and a narrow one: shoe polish and the
   * K01047 umbrella. Nothing else is ever given away.
   */
  get allowsFreeIssue(): boolean {
    return this.method === 'STATIONERY_ONLY';
  }

  /** Whether this particular line may carry a free quantity. */
  allowsFreeOn(line: LineEntry): boolean {
    return this.allowsFreeIssue
        && (this.isPolishItem(line.item) || this.isFreeUmbrella(line.item));
  }

  /**
   * The scheme is a starting point only. The rep says what they are actually giving
   * once the bill is totalled, so a typed figure is never overwritten.
   */
  autoFreeQty(line: LineEntry): number {
    if (!this.allowsFreeOn(line)) return 0;
    const buy  = line.item.freeIssueBuyQty;
    const free = line.item.freeIssueFreeQty;
    if (!buy || !free || buy <= 0) return 0;
    return Math.floor((line.qty || 0) / buy) * free;
  }

  /** Umbrellas going out free on this bill — nil unless the rep threw one in. */
  get freeUmbrellaQty(): number {
    return this.lines
      .filter(l => this.isFreeUmbrella(l.item))
      .reduce((s, l) => s + (l.freeQty || 0), 0);
  }

  // ── The K01047 umbrella ──────────────────────────────────────────────
  // The rep says at the end, once the bill is totalled, whether one went in. So it is
  // asked for below the total rather than buried in the line table — nobody thinks to
  // go and search the catalogue for an umbrella they were told about afterwards.

  /** The umbrella in the catalogue, if it is there at all. */
  get umbrellaItem(): Item | null {
    return this.items.find(i => this.isFreeUmbrella(i)) ?? null;
  }

  /** The umbrella question only arises on stationery bills. */
  get showUmbrellaBox(): boolean {
    return this.method === 'STATIONERY_ONLY';
  }

  get umbrellaLine(): LineEntry | null {
    return this.lines.find(l => this.isFreeUmbrella(l.item)) ?? null;
  }

  get umbrellaQty(): number {
    return this.umbrellaLine?.freeQty ?? 0;
  }

  /**
   * Adds, updates or removes the free-only umbrella line. Paid quantity stays zero:
   * it carries no value, but it still leaves the warehouse, so it has to be a real
   * line for the stock to move.
   */
  setUmbrellaQty(qty: number | string) {
    const n = Math.max(0, Math.floor(Number(qty) || 0));
    const item = this.umbrellaItem;
    if (!item) return;

    const existing = this.umbrellaLine;
    if (n === 0) {
      if (existing) this.lines = this.lines.filter(l => l !== existing);
    } else if (existing) {
      existing.freeQty = n;
      existing.freeTouched = true;
    } else {
      this.lines.push({ item, qty: 0, freeQty: n, freeTouched: true });
    }
    this.refreshQuote();
    this.cdr.markForCheck();
  }

  hasAutoScheme(line: LineEntry): boolean {
    return this.allowsFreeOn(line)
        && !!line.item.freeIssueBuyQty && !!line.item.freeIssueFreeQty;
  }

  /**
   * Re-applies the scheme when the quantity changes, unless someone has typed over it.
   * A hand-typed figure is what was actually given, so it is never overwritten.
   */
  onQtyChanged(line: LineEntry) {
    if (!line.freeTouched) line.freeQty = this.autoFreeQty(line);
    this.refreshQuote();
    this.cdr.markForCheck();
  }

  onFreeQtyChanged(line: LineEntry) {
    line.freeTouched = true;
    this.refreshQuote();
    this.cdr.markForCheck();
  }

  /** Back to whatever the scheme says. */
  resetFreeQty(line: LineEntry) {
    line.freeTouched = false;
    line.freeQty = this.autoFreeQty(line);
    this.cdr.markForCheck();
  }

  /**
   * Re-prices the draft. Debounced because it fires on every keystroke in a quantity box,
   * and sequence-checked so a slow earlier reply cannot overwrite a newer one.
   */
  refreshQuote() {
    if (this.quoteTimer) clearTimeout(this.quoteTimer);
    if (this.lines.length === 0) {
      this.quote = null;
      this.cdr.markForCheck();
      return;
    }
    this.quoteTimer = setTimeout(() => {
      const seq = ++this.quoteSeq;
      this.quoting = true;
      this.cdr.markForCheck();

      const v = this.form.getRawValue();
      this.svc.quote({
        invoiceType: v.invoiceType,
        plasticDiscountPct: v.plasticDiscountPct || undefined,
        plasticDiscountAmount: v.plasticDiscountAmount || undefined,
        lines: this.lines.map(l => ({ itemId: l.item.id, qty: l.qty, freeQty: l.freeQty || 0 })),
      }).pipe(catchError(() => of(null))).subscribe(res => {
        if (seq !== this.quoteSeq) return;   // a newer quote already came back
        this.quote = res;
        this.quoting = false;
        this.cdr.markForCheck();
      });
    }, 300);
  }

  totalFreeQty(): number { return this.lines.reduce((s, l) => s + (l.freeQty || 0), 0); }

  private itemAllowed(item: Item): boolean {
    if (this.method === 'RAINCO_ONLY') return item.category === 'RAINCO';
    if (this.method === 'STATIONERY_ONLY') {
      // The K01047 umbrella is a Rainco item given away on stationery bills, so it is
      // let through despite the category. Import already did this; typed invoices
      // could not, which meant the same bill behaved differently by route.
      return item.category === 'STATIONERY' || this.isFreeUmbrella(item);
    }
    if (this.method === 'PLASTIC_ONLY') return item.category === 'PLASTIC';
    return true;
  }

  private itemsForMethod(): Item[] {
    return this.items.filter(i => this.itemAllowed(i));
  }

  // ── Re-reading the item master mid-invoice ──────────────────────────
  // The catalogue is loaded once when the form opens, so a price fixed or an item added
  // in another tab is invisible to an invoice already in progress. Reloading and
  // starting again would throw the lines away, which is why this exists.

  refreshingItems = false;
  itemsRefreshedAt: Date | null = null;
  /** Lines whose item vanished from the catalogue on the last refresh. */
  droppedItems: string[] = [];

  refreshItems(): void {
    if (this.refreshingItems) return;
    this.refreshingItems = true;
    this.droppedItems = [];
    this.cdr.markForCheck();

    this.itemSvc.list().pipe(catchError(() => of([] as Item[]))).subscribe(items => {
      this.items = items ?? [];

      // Re-point the lines already on the invoice at the fresh records, so a corrected
      // price or stock figure reaches the draft instead of only the search box.
      // Quantities are the user's and are left exactly as they are.
      const byId = new Map(this.items.map(i => [i.id, i]));
      const kept: LineEntry[] = [];
      for (const line of this.lines) {
        const fresh = byId.get(line.item.id);
        if (fresh) {
          kept.push({ ...line, item: fresh });
        } else {
          // Deleted or deactivated while this invoice was open. Dropping it silently
          // would leave a line that cannot be saved and no explanation why.
          this.droppedItems.push(line.item.itemCode);
        }
      }
      this.lines = kept;

      // A method change may have made a free quantity illegal in the meantime.
      this.lines.forEach(l => {
        if (!this.allowsFreeOn(l)) { l.freeQty = 0; l.freeTouched = false; }
      });

      this.filteredItems = this.itemsForMethod();
      this.itemSearch = '';
      this.refreshingItems = false;
      this.itemsRefreshedAt = new Date();
      this.refreshQuote();
      this.cdr.markForCheck();
    });
  }

  filterCustomers(q: string | Customer) {
    if (typeof q !== 'string') return; // mat-autocomplete fires ngModelChange with the raw option value on selection
    this.customerSearch = q;
    if (!q) { this.filteredCustomers = this.customers; return; }
    const lq = q.toLowerCase();
    this.filteredCustomers = this.customers.filter(c =>
      c.name.toLowerCase().includes(lq)
    );
    this.cdr.markForCheck();
  }

  selectCustomer(c: Customer) {
    this.form.patchValue({ customerId: c.id });
    this.customerSearch = c.name;
    this.cdr.markForCheck();
  }

  customerDisplayFn = (c: Customer | string): string =>
    typeof c === 'string' ? c : (c?.name ?? '');

  filterItems(q: string | Item) {
    if (typeof q !== 'string') return; // mat-autocomplete fires ngModelChange with the raw option value on selection
    this.itemSearch = q;
    const scoped = this.itemsForMethod();
    if (!q) { this.filteredItems = scoped; return; }
    const lq = q.toLowerCase();
    this.filteredItems = scoped.filter(i =>
      i.itemCode.toLowerCase().includes(lq) || i.description.toLowerCase().includes(lq)
    );
    this.cdr.markForCheck();
  }

  itemDisplayFn = (value: string | Item): string => typeof value === 'string' ? value : '';

  addLine(item: Item) {
    if (this.lines.find(l => l.item.id === item.id)) {
      const existing = this.lines.find(l => l.item.id === item.id)!;
      existing.qty++;
      if (!existing.freeTouched) existing.freeQty = this.autoFreeQty(existing);
    } else {
      const line: LineEntry = { item, qty: 1, freeQty: 0 };
      line.freeQty = this.autoFreeQty(line);
      this.lines.push(line);
    }
    this.itemSearch = '';
    this.filteredItems = this.itemsForMethod();
    this.refreshQuote();
    this.cdr.markForCheck();
  }

  removeLine(idx: number) {
    this.lines.splice(idx, 1);
    this.refreshQuote();
    this.cdr.markForCheck();
  }

  lineWsp(line: LineEntry) {
    return line.item.wsp ?? line.item.wholesalePrice ?? 0;
  }

  lineTotal(line: LineEntry) {
    return this.lineWsp(line) * line.qty;
  }

  get grossTotal() {
    return this.lines.reduce((sum, l) => sum + this.lineTotal(l), 0);
  }

  get method(): InvoiceMethod { return this.form.value.method as InvoiceMethod; }

  get showPlastic() {
    return this.method === 'MIX' || this.method === 'RAINCO_ONLY' || this.method === 'PLASTIC_ONLY';
  }

  /** Optional when typed here — the number comes from the book, not the agent's copy. */
  get showAgentRef() { return true; }

  // ── Bill number, exactly as the bills section does it ────────────────
  // The invoice number IS the bill number, so the type and number are picked here
  // the same way they are on the create-bill form.

  get business(): string {
    switch (this.method) {
      case 'RAINCO_ONLY':     return 'RAINCO';
      case 'STATIONERY_ONLY': return 'STATIONERY';
      case 'PLASTIC_ONLY':    return 'PLASTIC';
      default:                return 'MIX';
    }
  }

  /** Rainco keeps its own manual book; Plastic and Stationery share the BK- book. */
  get billSources(): string[] {
    return this.business === 'RAINCO'
      ? ['SYSTEM', 'MANUAL', 'MANUAL_BOOK']
      : ['SYSTEM', 'MANUAL'];
  }

  billSourceLabel(s: string): string {
    switch (s) {
      case 'SYSTEM':      return 'System (SYS-)';
      case 'MANUAL_BOOK': return 'Manual Book (BK-)';
      case 'MANUAL':      return this.business === 'RAINCO' ? 'Manual (MAN-)' : 'Manual Book (BK-)';
      default:            return s;
    }
  }

  numberPrefix(): string {
    const src = this.form.value.billSource;
    if (src === 'SYSTEM') return 'SYS-';
    if (src === 'MANUAL_BOOK') return 'BK-';
    return (this.business === 'PLASTIC' || this.business === 'STATIONERY') ? 'BK-' : 'MAN-';
  }

  previewNumber(): string {
    const n = String(this.form.value.billNumber || '').trim();
    return n ? this.numberPrefix() + n : '';
  }

  missingCount(): number { return this.suggestedNumbers.filter(n => n.missing).length; }

  /** The next unused number, offered as a shortcut rather than filled in silently. */
  get nextFreeNumber(): number | null {
    return this.suggestedNumbers.find(n => !n.missing)?.number ?? null;
  }

  get numberChosen(): boolean {
    return !!String(this.form.value.billNumber || '').trim();
  }

  useNextNumber(): void {
    const n = this.nextFreeNumber;
    if (n != null) {
      this.form.get('billNumber')!.setValue(String(n));
      this.form.get('billNumber')!.markAsTouched();
      this.cdr.markForCheck();
    }
  }

  loadNumbers(): void {
    const source = this.form.value.billSource;
    if (!source) return;
    this.loadingNumbers = true;
    this.cdr.markForCheck();
    this.billApi.getNextBillNumbers(this.business, source).subscribe({
      next: nums => {
        this.suggestedNumbers = nums;
        // Deliberately left blank. The number is also the bill number, so a save
        // consumes it and raises a bill against it — too costly to let a stray click
        // pick one. The user chooses, or the form will not submit.
        this.loadingNumbers = false;
        this.cdr.markForCheck();
      },
      error: () => { this.loadingNumbers = false; this.cdr.markForCheck(); },
    });
  }

  submit() {
    if (this.form.invalid || this.lines.length === 0) return;
    // A line has to give something — either sold or free.
    if (this.lines.some(l => (l.qty || 0) <= 0 && (l.freeQty || 0) <= 0)) {
      this.error = 'Every line needs a quantity, either paid or free.';
      this.cdr.markForCheck();
      return;
    }
    this.saving = true;
    this.error  = '';
    const v = this.form.getRawValue();  // getRawValue includes the disabled fields
    const req = {
      method:                v.method,
      invoiceType:           v.invoiceType,
      customerId:            v.customerId,
      invoiceDate:           v.invoiceDate,
      externalRef:           (v.externalRef || '').trim() || undefined,
      billSource:            v.billSource,
      billNumber:            String(v.billNumber || '').trim(),
      agentPrintedNet:       v.agentPrintedNet || undefined,
      plasticDiscountPct:    v.plasticDiscountPct || undefined,
      plasticDiscountAmount: v.plasticDiscountAmount || undefined,
      lines: this.lines.map(l => ({ itemId: l.item.id, qty: l.qty, freeQty: l.freeQty || 0 }))
    };
    const call = this.editingId
      ? this.svc.update(this.editingId, req)
      : this.svc.create(req);

    call.pipe(catchError(err => {
      this.error = err?.error?.message
                 ?? (this.editingId ? 'Failed to save the changes' : 'Failed to create invoice');
      this.saving = false;
      this.cdr.markForCheck();
      return of(null);
    })).subscribe(res => {
      this.saving = false;
      if (res) this.router.navigate(['/invoicing/invoices', res.id]);
      this.cdr.markForCheck();
    });
  }

  cancel() { this.router.navigate(['/invoicing/invoices']); }
}
