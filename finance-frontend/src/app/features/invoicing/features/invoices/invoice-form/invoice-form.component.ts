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
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { localDateStr } from '../../../../../core/utils/date-utils';
import { catchError, of } from 'rxjs';
import { InvoiceService } from '../../../core/services/invoice.service';
import { ItemService } from '../../../core/services/item.service';
import { CustomerService } from '../../../core/services/customer.service';
import { Item, Customer, InvoiceMethod, InvoiceType, Quote } from '../../../core/models/models';
import { Bill, BillNumberOption } from '../../../../../core/services/bill';
import { Auth } from '../../../../../core/services/auth';

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
            MatSelectModule, MatProgressSpinnerModule, MatTooltipModule, MatAutocompleteModule,
            MatDatepickerModule, MatNativeDateModule],
  templateUrl: './invoice-form.component.html',
  styleUrl: './invoice-form.component.scss'
})
export class InvoiceFormComponent implements OnInit {
  private billApi = inject(Bill);
  private auth = inject(Auth);

  /** The promotional discount is an admin decision, not a clerical one. */
  get isAdmin(): boolean { return this.auth.getRole() === 'ADMIN'; }

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
    // A Date rather than a string: the picker works in Date, and the value is
    // formatted back to YYYY-MM-DD on save.
    invoiceDate:         [new Date() as Date | null,  Validators.required],
    externalRef:         [''],
    billSource:          ['SYSTEM', Validators.required],
    billNumber:          ['', Validators.required],
    agentPrintedNet:     [null as number | null],
    plasticDiscountPct:  [null as number | null],
    plasticDiscountAmount: [null as number | null],
    // Admin only: a flat rate replacing the slab, for promotions.
    discountOverridePct: [null as number | null]
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
    this.form.get('discountOverridePct')!.valueChanges.subscribe(() => this.refreshQuote());

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
      // Imported invoices carry the agent's number; typed ones no longer ask for it.
      this.hasExternalRef = !!(inv.externalRef ?? '').trim();
      this.form.patchValue({
        method: inv.method,
        invoiceType: inv.invoiceType,
        customerId: inv.customerId,
        invoiceDate: inv.invoiceDate ? new Date(String(inv.invoiceDate).slice(0, 10)) : null,
        externalRef: inv.externalRef ?? '',
        agentPrintedNet: inv.agentPrintedNet ?? null,
        plasticDiscountPct: inv.plasticDiscountPct ?? null,
        plasticDiscountAmount: inv.plasticDiscountAmount ?? null,
        discountOverridePct: inv.discountOverridePct ?? null,
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
        discountOverridePct: v.discountOverridePct ?? undefined,
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

  // ── Pasting lines instead of typing them ────────────────────────────
  // The lines come off a photo of the agent's invoice, read by an LLM into
  // "code,qty,price". Only the code and quantity are used to build the invoice: the
  // price is a check, not an input. Matching on the code and then verifying the price
  // means a mistyped code is caught, rather than a misread price quietly selecting a
  // different item.

  /**
   * The inline adder sitting under the last line, so another item can be added where
   * the eye already is rather than by scrolling back to the search at the top.
   * It offers only items of the chosen bill category, the same list the search uses.
   */
  showInlineAdd = false;
  inlineSearch = '';
  inlineFiltered: Item[] = [];

  toggleInlineAdd() {
    this.showInlineAdd = !this.showInlineAdd;
    this.inlineSearch = '';
    this.inlineFiltered = this.itemsForMethod().slice(0, 25);
    this.cdr.markForCheck();
  }

  filterInline(q: string) {
    this.inlineSearch = q;
    const scoped = this.itemsForMethod();
    const lq = (q ?? '').toLowerCase().trim();
    this.inlineFiltered = (!lq ? scoped : scoped.filter(i =>
      i.itemCode.toLowerCase().includes(lq) || i.description.toLowerCase().includes(lq)
    )).slice(0, 25);
    this.cdr.markForCheck();
  }

  /** Adds the picked item and stays open, so several can be added in a row. */
  addInline(item: Item) {
    this.addLine(item);
    this.inlineSearch = '';
    this.inlineFiltered = this.itemsForMethod().slice(0, 25);
    this.cdr.markForCheck();
  }

  // ── Re-reading the customer list mid-invoice ────────────────────────
  // Customers are loaded once when the form opens, so one added in another tab is
  // invisible to an invoice already started. Reloading the page would lose the lines.

  refreshingCustomers = false;
  customersRefreshedAt: Date | null = null;

  refreshCustomers() {
    if (this.refreshingCustomers) return;
    this.refreshingCustomers = true;
    this.cdr.markForCheck();

    this.custSvc.list().pipe(catchError(() => of([] as Customer[]))).subscribe(list => {
      this.customers = list ?? [];

      // Keep whoever is already chosen selected, and keep their name in the box —
      // a reload that silently cleared the customer would be worse than not reloading.
      const chosenId = this.form.value.customerId;
      const stillThere = this.customers.find(c => c.id === chosenId);
      if (chosenId != null && !stillThere) {
        this.form.get('customerId')!.setValue(null);
        this.customerSearch = '';
      } else if (stillThere) {
        this.customerSearch = stillThere.name;
      }

      this.filteredCustomers = this.customers;
      this.refreshingCustomers = false;
      this.customersRefreshedAt = new Date();
      this.cdr.markForCheck();
    });
  }

  showPaste = false;
  showPastePrompt = false;
  promptCopied = false;
  pasteText = '';

  /**
   * The instruction to hand an AI along with a photo of the agent's invoice.
   *
   * <p>Kept here rather than in a note somewhere, so the wording the parser expects and
   * the wording the user actually sends can never drift apart. UNREADABLE is asked for
   * deliberately: a guessed digit is worse than a gap somebody gets asked about.
   */
  readonly aiPrompt = [
    'Read this invoice image. Output ONLY CSV, no explanation, no code fences.',
    '',
    'First line exactly:',
    'code,qty,price',
    '',
    'Then one row per item line:',
    '- code: the item code exactly as printed, no spaces added or removed',
    '- qty: the quantity as a plain number',
    '- price: the unit price as a plain number, no thousands separators (2050.00)',
    '',
    'Rules:',
    '- Transcribe codes and prices exactly. Do not round, correct or guess.',
    '- If a value is unreadable, write UNREADABLE rather than guessing.',
    '- Skip subtotal, discount and total lines. Item rows only.',
  ].join('\n');

  togglePastePrompt() {
    this.showPastePrompt = !this.showPastePrompt;
    this.cdr.markForCheck();
  }

  copyPrompt() {
    navigator.clipboard.writeText(this.aiPrompt).then(() => {
      this.promptCopied = true;
      this.cdr.markForCheck();
      setTimeout(() => { this.promptCopied = false; this.cdr.markForCheck(); }, 2000);
    }).catch(() => {
      // Clipboard is blocked on insecure origins; the prompt is on screen to select.
      this.showPastePrompt = true;
      this.cdr.markForCheck();
    });
  }
  pasteResult: {
    added: number;
    unknown: string[];
    ambiguous: string[];
    mismatched: { code: string; pasted: number; catalog: number }[];
  } | null = null;

  togglePaste() {
    this.showPaste = !this.showPaste;
    if (!this.showPaste) { this.pasteText = ''; this.pasteResult = null; }
    this.cdr.markForCheck();
  }

  /** Digits only, leading zeros dropped — K01047 and 1047 compare equal. */
  private codeDigits(code: string): string {
    return (code.match(/\d+/g) ?? []).join('').replace(/^0+/, '');
  }

  private itemPrice(i: Item): number {
    return Number(i.wsp ?? i.wholesalePrice ?? 0);
  }

  /**
   * Narrows several candidates to one using the pasted price.
   *
   * <p>This is what the price is for. A code like 1020 exists twice in the catalogue —
   * RC-1020N at 779.00 and RC-1020 at 729.80 — and the code alone cannot separate
   * them. The price on the agent's line can, and does so exactly: it is the figure
   * that item was sold at.
   */
  private narrowByPrice(candidates: Item[], price: number): Item[] {
    if (!Number.isFinite(price) || price <= 0) return candidates;
    const exact = candidates.filter(i => Math.abs(this.itemPrice(i) - price) < 0.01);
    if (exact.length) return exact;
    // Nothing exact — allow a rounding tail before giving up.
    return candidates.filter(i => Math.abs(this.itemPrice(i) - price) < 0.5);
  }

  /**
   * Resolves a pasted code to one catalogue item.
   *
   * <p>Code first, price second. Where the code lands on several items the price
   * decides between them, rather than the whole line being skipped — which is what
   * used to happen to every code that exists in more than one variant.
   */
  private findByCode(code: string, price: number): { item?: Item; candidates?: Item[] } {
    const wanted = code.trim().toUpperCase();

    const exact = this.items.filter(i => (i.itemCode ?? '').trim().toUpperCase() === wanted);
    if (exact.length === 1) return { item: exact[0] };
    if (exact.length > 1) {
      const narrowed = this.narrowByPrice(exact, price);
      if (narrowed.length === 1) return { item: narrowed[0] };
      return { candidates: exact };
    }

    // The agent's sheet writes 1020 where the catalogue holds RC-1020N, so the digits
    // are compared when the code itself does not match.
    const digits = this.codeDigits(code);
    if (!digits) return {};
    const byDigits = this.items.filter(i => this.codeDigits(i.itemCode ?? '') === digits);
    if (byDigits.length === 1) return { item: byDigits[0] };
    if (byDigits.length > 1) {
      const narrowed = this.narrowByPrice(byDigits, price);
      if (narrowed.length === 1) return { item: narrowed[0] };
      return { candidates: byDigits };
    }
    return {};
  }

  /**
   * Reads the pasted block and adds a line per row.
   *
   * <p>Accepts comma, tab or spaces between the fields, and ignores a header row, so
   * whatever the LLM emits can go straight in without tidying.
   */
  applyPaste() {
    const unknown: string[] = [];
    const ambiguous: string[] = [];
    const mismatched: { code: string; pasted: number; catalog: number }[] = [];
    let added = 0;

    for (const raw of this.pasteText.split(/\r?\n/)) {
      const line = raw.trim();
      if (!line) continue;
      // A header row, or a note the model added despite being told not to.
      if (/^code\b/i.test(line)) continue;

      const parts = line.split(/[,\t]+|\s{2,}|\s+/).map(t => t.trim()).filter(Boolean);
      if (parts.length < 2) continue;

      const code = parts[0];
      const qty = Number(parts[1]);
      const price = parts.length > 2 ? Number(parts[2]) : NaN;
      if (!Number.isFinite(qty) || qty <= 0) continue;

      const found = this.findByCode(code, price);
      if (found.candidates) {
        // Say which items it could be and what they cost — the price that would have
        // separated them is the thing the user needs to supply or correct.
        ambiguous.push(
          `${code} → ` +
          found.candidates
            .map(c => `${c.itemCode} @ ${this.itemPrice(c).toFixed(2)}`)
            .join(' | ')
        );
        continue;
      }
      if (!found.item) { unknown.push(code); continue; }

      const item = found.item;
      if (!this.itemAllowed(item)) { unknown.push(code + ' (wrong category)'); continue; }

      // The price is only ever a check on the code. A disagreement is reported and the
      // catalogue's own figure is used, because that is what the invoice is priced on.
      if (Number.isFinite(price) && price > 0) {
        const catalog = Number(item.wsp ?? item.wholesalePrice ?? 0);
        if (catalog > 0 && Math.abs(catalog - price) > 0.5) {
          mismatched.push({ code: item.itemCode, pasted: price, catalog });
        }
      }

      const existing = this.lines.find(l => l.item.id === item.id);
      if (existing) {
        existing.qty += qty;
        if (!existing.freeTouched) existing.freeQty = this.autoFreeQty(existing);
      } else {
        const entry: LineEntry = { item, qty, freeQty: 0 };
        entry.freeQty = this.autoFreeQty(entry);
        this.lines.push(entry);
      }
      added++;
    }

    this.pasteResult = { added, unknown, ambiguous, mismatched };
    if (added > 0) this.pasteText = '';
    this.filteredItems = this.itemsForMethod();
    this.refreshQuote();
    this.cdr.markForCheck();
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

  /**
   * Only invoices that can carry plastic lines. A Rainco-only or Stationery-only
   * invoice has no plastic on it, so a plastic discount box is a field that can only
   * ever be filled in by mistake.
   */
  get showPlastic() {
    return this.method === 'MIX' || this.method === 'PLASTIC_ONLY';
  }

  /** True when this invoice was imported and carries the agent's own reference. */
  hasExternalRef = false;

  /**
   * The agent reference is no longer asked for when typing an invoice: the bill number
   * IS the reference now, so a second field for it only invited them to disagree. It
   * still shows when editing an imported invoice, which does carry the agent's number.
   */
  get showAgentRef() { return this.hasExternalRef; }

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
      // localDateStr, never toISOString: the latter shifts by the timezone offset and
      // would file an evening invoice under the following day.
      invoiceDate:           v.invoiceDate ? localDateStr(new Date(v.invoiceDate)) : localDateStr(),
      // Not sent when typing: the bill number is the reference. On an edit the server
      // leaves the stored value alone when this is absent, so an imported invoice keeps
      // the agent's number.
      externalRef:           this.hasExternalRef
                               ? ((v.externalRef || '').trim() || undefined)
                               : undefined,
      billSource:            v.billSource,
      billNumber:            String(v.billNumber || '').trim(),
      agentPrintedNet:       v.agentPrintedNet || undefined,
      plasticDiscountPct:    v.plasticDiscountPct || undefined,
      plasticDiscountAmount: v.plasticDiscountAmount || undefined,
      discountOverridePct:   v.discountOverridePct ?? undefined,
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
