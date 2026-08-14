import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap, catchError } from 'rxjs/operators';
import { of } from 'rxjs';
import { Bill, BillResponse } from '../../../core/services/bill';
import { BillReturnService, BillReturnItemRequest, ReturnableLine }
  from '../../../core/services/bill-return';
import { ReturnProductService, ReturnProductResponse } from '../../../core/services/return-product';
import { StockService } from '../../../core/services/stock';
import { Worker, WorkerResponse } from '../../../core/services/worker';

interface LineItem {
  productId?: number;
  itemName: string;
  unitPrice: number;
  quantityRequested: number;
  quantityReturned?: number;
  lineTotal: number;

  /** Set when the goods came off this bill's own invoice line. */
  invoiceLineId?: number;
  itemId?: number;
  /** The most this line can still give back. */
  qtyAvailable?: number;

  /** Read off the invoice line for a same-bill return; typed otherwise. */
  slabDiscountPct?: number | null;
  /** What the calculation produces, before any override. */
  computedCredit: number;
  /** Typed over the computed figure - flagged to the admin on review. */
  creditOverride?: number | null;
}

@Component({
  selector: 'app-submit-return',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    RouterLink,
    DecimalPipe,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './submit-return.html',
  styleUrl: './submit-return.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SubmitReturn implements OnInit {
  billResults: BillResponse[] = [];
  billSearching = false;
  private billSearch$ = new Subject<string>();

  selectedBill: BillResponse | null = null;
  products: ReturnProductResponse[] = [];
  workers: WorkerResponse[] = [];

  searchQuery = '';
  productSearch = '';
  filteredProducts: ReturnProductResponse[] = [];
  showProductDropdown = false;

  returnType = '';

  /**
   * Where the goods came from. Off this bill, the price and discount are the ones
   * actually charged; off an older bill they have to be typed.
   */
  sourceMode: 'BILL' | 'CATALOGUE' = 'BILL';
  returnableLines: ReturnableLine[] = [];
  loadingLines = false;
  /** Only asked for a different-bill return - a same-bill one already knows. */
  cashSale = false;

  items: LineItem[] = [];
  discountPercentage: number | null = null;
  discountFixed: number | null = null;
  predictedValue: number | null = null;
  responsibleWorkerId: number | null = null;
  notes = '';

  submitting = false;
  errorMsg = '';
  submitted = false;
  lastSubmittedBill: { id: number; billNumber: string; customerName: string } | null = null;

  returnTypes = ['DAMAGE', 'SALABLE'];

  get selectedBusiness(): string {
    return this.selectedBill?.business ?? '';
  }

  get isPlastic(): boolean {
    return this.selectedBusiness === 'PLASTIC';
  }

  get isSalable(): boolean {
    return this.returnType === 'SALABLE';
  }

  /** Only a bill with an invoice behind it can be returned against line by line. */
  get canPickFromBill(): boolean {
    return this.returnableLines.length > 0;
  }

  get fromSameBill(): boolean {
    return this.sourceMode === 'BILL' && this.canPickFromBill;
  }

  /**
   * The 5% cash discount is a Rainco arrangement. Stationery and plastic are sold at
   * the same price either way, so there is nothing to credit back and the question is
   * not even asked.
   */
  get cashOffered(): boolean {
    if (this.fromSameBill) return this.returnableLines.some(l => !!l.cashDiscountPct);
    return this.selectedBusiness === 'RAINCO' || this.selectedBusiness === 'MIX';
  }

  /** A same-bill return reads the cash answer off the bill rather than asking. */
  get cashApplies(): boolean {
    if (!this.cashOffered) return false;
    return this.fromSameBill ? true : this.cashSale;
  }

  /**
   * Per line, not per bill: a MIX bill carries stationery and plastic alongside Rainco,
   * and only the Rainco part ever had the discount taken.
   */
  lineCashPct(item: LineItem): number {
    if (!this.cashApplies) return 0;
    if (item.invoiceLineId) {
      const src = this.returnableLines.find(l => l.invoiceLineId === item.invoiceLineId);
      return src?.cashDiscountPct ?? 0;
    }
    // Catalogue pick: the bill's business is all there is to go on.
    return this.selectedBusiness === 'RAINCO' ? (this.cashPct || 5) : 0;
  }

  /** Headline rate, for the chip and the working panel. */
  get cashPct(): number {
    if (!this.cashOffered) return 0;
    return this.returnableLines.find(l => !!l.cashDiscountPct)?.cashDiscountPct ?? 5;
  }

  get itemsTotal(): number {
    return this.items.reduce((s, i) => s + i.unitPrice * i.quantityRequested, 0);
  }

  /** What the customer is actually credited, after each line's discounts. */
  get creditTotal(): number {
    return this.items.reduce((s, i) => s + this.lineCredit(i), 0);
  }

  get slabTotal(): number {
    return this.items.reduce(
      (s, i) => s + i.unitPrice * i.quantityRequested - this.afterSlab(i), 0);
  }

  get cashTotal(): number {
    return this.items.reduce((s, i) => s + this.afterSlab(i) - i.computedCredit, 0);
  }

  get anyEdited(): boolean {
    return this.items.some(i => i.creditOverride != null);
  }

  /**
   * Mirrors the server calculator: WSP x qty, less the line discount, less cash.
   * Plastic is sold flat, so it is credited flat.
   */
  private afterSlab(item: LineItem): number {
    const gross = item.unitPrice * item.quantityRequested;
    if (this.isPlastic) return gross;
    const pct = item.slabDiscountPct ?? 0;
    return gross - (gross * pct) / 100;
  }

  computeCredit(item: LineItem): number {
    if (this.isPlastic) return item.unitPrice * item.quantityRequested;
    const afterSlab = this.afterSlab(item);
    return Math.max(0, afterSlab - (afterSlab * this.lineCashPct(item)) / 100);
  }

  lineCredit(item: LineItem): number {
    return item.creditOverride != null ? item.creditOverride : item.computedCredit;
  }

  get calculatedAmount(): number {
    let amount = this.creditTotal;
    if (this.discountFixed && this.discountFixed > 0) {
      amount -= this.discountFixed;
    }
    return Math.max(0, amount);
  }

  get canSubmit(): boolean {
    return !!this.selectedBill && !!this.returnType && this.items.length > 0 && !this.submitting;
  }

  constructor(
    private billService: Bill,
    private billReturnService: BillReturnService,
    private returnProductService: ReturnProductService,
    private stockService: StockService,
    private workerService: Worker,
    private router: Router,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.workerService.getAllWorkers().subscribe({
      next: (w: WorkerResponse[]) => { this.workers = w; this.cdr.markForCheck(); },
      error: () => {},
    });

    this.billSearch$.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap(q => {
        if (!q || q.length < 2) {
          this.billResults = [];
          this.billSearching = false;
          this.cdr.markForCheck();
          return of([]);
        }
        this.billSearching = true;
        this.cdr.markForCheck();
        return this.billService.globalSearch(q).pipe(
          catchError(() => of([]))
        );
      }),
    ).subscribe(results => {
      this.billResults = (results as BillResponse[]).filter(b => b.status !== 'COMPLETED');
      this.billSearching = false;
      this.cdr.markForCheck();
    });
  }

  onBillSearchInput(): void {
    this.billSearch$.next(this.searchQuery.trim());
  }

  selectBill(bill: BillResponse): void {
    this.selectedBill = bill;
    this.items = [];
    this.products = [];
    this.filteredProducts = [];
    this.productSearch = '';
    this.returnType = '';
    this.discountPercentage = null;
    this.discountFixed = null;
    this.predictedValue = null;
    this.searchQuery = `${bill.billNumber} — ${bill.customerName}`;
    this.billResults = [];
    this.submitted = false;
    this.errorMsg = '';
    this.returnableLines = [];
    this.cashSale = false;
    this.loadingLines = true;
    this.cdr.markForCheck();

    // Bills entered before invoicing have no lines behind them; those fall back to
    // the catalogue with the discount typed by hand.
    this.billReturnService.getReturnableLines(bill.id).subscribe({
      next: (lines) => {
        this.returnableLines = lines;
        this.sourceMode = lines.length > 0 ? 'BILL' : 'CATALOGUE';
        this.loadingLines = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.returnableLines = [];
        this.sourceMode = 'CATALOGUE';
        this.loadingLines = false;
        this.cdr.markForCheck();
      },
    });
  }

  submitAnother(): void {
    this.submitted = false;
    this.returnType = '';
    this.items = [];
    this.products = [];
    this.filteredProducts = [];
    this.productSearch = '';
    this.discountPercentage = null;
    this.discountFixed = null;
    this.predictedValue = null;
    this.responsibleWorkerId = null;
    this.notes = '';
    this.errorMsg = '';
    // Keep selectedBill and searchQuery so they don't need to re-select
    this.cdr.markForCheck();
  }

  onReturnTypeChange(): void {
    this.products = [];
    this.filteredProducts = [];
    this.productSearch = '';
    this.items = [];
    if (!this.selectedBill || this.selectedBill.business === 'PLASTIC') return;

    if (this.returnType === 'DAMAGE') {
      this.returnProductService.getByBusiness(this.selectedBill.business).subscribe({
        next: (p) => { this.products = p; this.cdr.detectChanges(); },
        error: () => {},
      });
    } else if (this.returnType === 'SALABLE') {
      this.stockService.getRaincoProducts().subscribe({
        next: (p) => { this.products = p; this.cdr.detectChanges(); },
        error: () => {},
      });
    }
  }

  clearBill(): void {
    this.selectedBill = null;
    this.searchQuery = '';
    this.billResults = [];
    this.items = [];
    this.products = [];
    this.filteredProducts = [];
    this.productSearch = '';
    this.showProductDropdown = false;
    this.returnType = '';
    this.cdr.markForCheck();
  }

  filterProducts(): void {
    const q = this.productSearch.toLowerCase().trim();
    if (!q) {
      this.filteredProducts = [];
      this.showProductDropdown = false;
      this.cdr.detectChanges();
      return;
    }
    this.filteredProducts = this.products
      .filter(p => p.name.toLowerCase().includes(q))
      .slice(0, 10);
    this.showProductDropdown = this.filteredProducts.length > 0;
    this.cdr.detectChanges();
  }

  selectProductFromSearch(product: ReturnProductResponse): void {
    this.addProductItem(product);
    this.productSearch = '';
    this.filteredProducts = [];
    this.showProductDropdown = false;
    this.cdr.detectChanges();
  }

  hideProductDropdown(): void {
    setTimeout(() => {
      this.showProductDropdown = false;
      this.cdr.detectChanges();
    }, 150);
  }

  addProductItem(product: ReturnProductResponse): void {
    const existing = this.items.find(i => i.productId === product.id);
    if (existing) {
      existing.quantityRequested += 1;
      this.updateLineTotal(existing);
    } else {
      const item: LineItem = {
        productId: product.id,
        itemName: product.name,
        unitPrice: product.unitPrice,
        quantityRequested: 1,
        slabDiscountPct: 0,
        computedCredit: 0,
        lineTotal: 0,
      };
      item.computedCredit = this.computeCredit(item);
      item.lineTotal = item.computedCredit;
      this.items.push(item);
    }
    this.cdr.detectChanges();
  }

  addManualItem(): void {
    this.items.push({
      itemName: '',
      unitPrice: 0,
      quantityRequested: 1,
      slabDiscountPct: 0,
      computedCredit: 0,
      lineTotal: 0,
    });
    this.cdr.detectChanges();
  }

  updateLineTotal(item: LineItem): void {
    // Never let a line give back more than it has left on the bill.
    if (item.qtyAvailable != null && item.quantityRequested > item.qtyAvailable) {
      item.quantityRequested = item.qtyAvailable;
    }
    item.computedCredit = this.computeCredit(item);
    item.lineTotal = this.lineCredit(item);
    this.cdr.detectChanges();
  }

  /** Recompute every line - the cash chip moves all of them at once. */
  recalcAll(): void {
    this.items.forEach(i => {
      i.computedCredit = this.computeCredit(i);
      i.lineTotal = this.lineCredit(i);
    });
    this.cdr.detectChanges();
  }

  resetOverride(item: LineItem): void {
    item.creditOverride = null;
    this.updateLineTotal(item);
  }

  /** Adds one of the bill's own invoice lines, with its charged price and discount. */
  addBillLine(line: ReturnableLine): void {
    if (line.qtyAvailable <= 0) return;
    const existing = this.items.find(i => i.invoiceLineId === line.invoiceLineId);
    if (existing) {
      if (existing.quantityRequested < line.qtyAvailable) existing.quantityRequested += 1;
      this.updateLineTotal(existing);
      return;
    }
    const item: LineItem = {
      invoiceLineId: line.invoiceLineId,
      itemId: line.itemId,
      itemName: line.description,
      unitPrice: line.wsp,
      quantityRequested: 1,
      qtyAvailable: line.qtyAvailable,
      slabDiscountPct: line.appliedDiscountPct ?? 0,
      computedCredit: 0,
      lineTotal: 0,
    };
    item.computedCredit = this.computeCredit(item);
    item.lineTotal = item.computedCredit;
    this.items.push(item);
    this.cdr.detectChanges();
  }

  isLineAdded(line: ReturnableLine): boolean {
    return this.items.some(i => i.invoiceLineId === line.invoiceLineId);
  }

  // ── Re-reading the item data mid-return ─────────────────────────────
  // Both sources are read once when the bill and type are chosen, so a price corrected
  // in Items is invisible to a return already half entered. Reloading the page would
  // throw the lines away, which is the whole reason this exists.

  refreshing = false;
  refreshedAt: Date | null = null;
  /** Lines whose item is no longer available — named rather than dropped in silence. */
  droppedItems: string[] = [];

  refreshProducts(): void {
    if (this.refreshing || !this.selectedBill) return;
    this.refreshing = true;
    this.droppedItems = [];
    this.cdr.markForCheck();

    const billId = this.selectedBill.id;

    // The bill's own lines carry the price and discount actually charged, so they are
    // refreshed too — an edited invoice changes what can still be sent back.
    this.billReturnService.getReturnableLines(billId).subscribe({
      next: (lines) => {
        this.returnableLines = lines;
        this.reprice();
        this.finishRefresh();
      },
      error: () => this.finishRefresh(),
    });

    // The catalogue behind a different-bill return.
    if (!this.isPlastic && this.returnType) {
      const source$ = this.returnType === 'DAMAGE'
        ? this.returnProductService.getByBusiness(this.selectedBill.business)
        : this.stockService.getRaincoProducts();

      source$.subscribe({
        next: (p: ReturnProductResponse[]) => {
          this.products = p;
          this.filterProducts();
          this.reprice();
          this.cdr.markForCheck();
        },
        error: () => {},
      });
    }
  }

  private finishRefresh(): void {
    this.refreshing = false;
    this.refreshedAt = new Date();
    this.cdr.markForCheck();
  }

  /**
   * Re-points the lines already entered at the fresh figures, so a corrected price
   * reaches the return rather than only the picker. Quantities are the user's and are
   * left exactly as typed.
   */
  private reprice(): void {
    const kept: LineItem[] = [];

    for (const item of this.items) {
      if (item.invoiceLineId) {
        const src = this.returnableLines.find(l => l.invoiceLineId === item.invoiceLineId);
        if (!src) { this.droppedItems.push(item.itemName); continue; }
        item.unitPrice = src.wsp;
        item.slabDiscountPct = src.appliedDiscountPct ?? 0;
        item.qtyAvailable = src.qtyAvailable;
        // The bill may have taken another return since; never claim more than is left.
        if (item.quantityRequested > src.qtyAvailable) {
          item.quantityRequested = Math.max(src.qtyAvailable, 0);
        }
      } else if (item.productId) {
        const src = this.products.find(p => p.id === item.productId);
        if (!src) { this.droppedItems.push(item.itemName); continue; }
        item.unitPrice = src.unitPrice;
        item.itemName = src.name;
      }

      // A typed-over credit stays the user's figure; only the calculation moves.
      item.computedCredit = this.computeCredit(item);
      item.lineTotal = this.lineCredit(item);
      kept.push(item);
    }

    this.items = kept.filter(i => i.quantityRequested > 0 || !i.invoiceLineId);
  }

  setSourceMode(mode: 'BILL' | 'CATALOGUE'): void {
    this.sourceMode = mode;
    this.items = [];
    this.cdr.detectChanges();
  }

  removeItem(index: number): void {
    this.items.splice(index, 1);
    this.cdr.detectChanges();
  }

  submit(): void {
    if (!this.canSubmit) return;

    const invalid = this.items.find(i => !i.itemName || i.unitPrice <= 0 || i.quantityRequested < 1);
    if (invalid) {
      this.errorMsg = 'All items must have a name, unit price, and quantity.';
      this.cdr.detectChanges();
      return;
    }

    this.submitting = true;
    this.errorMsg = '';

    const payload = {
      returnType: this.returnType,
      fromSameBill: this.fromSameBill,
      cashSale: this.cashApplies,
      items: this.items.map(i => ({
        productId: i.productId ?? undefined,
        invoiceLineId: i.invoiceLineId ?? undefined,
        itemId: i.itemId ?? undefined,
        itemName: i.productId ? undefined : i.itemName,
        unitPrice: i.productId ? undefined : i.unitPrice,
        quantityRequested: i.quantityRequested,
        quantityReturned: i.quantityReturned ?? undefined,
        slabDiscountPct: i.slabDiscountPct ?? undefined,
        creditAmountOverride: i.creditOverride ?? undefined,
      } as BillReturnItemRequest)),
      discountPercentage: this.discountPercentage ?? undefined,
      discountFixed: this.discountFixed ?? undefined,
      predictedValue: this.predictedValue ?? undefined,
      responsibleWorkerId: this.responsibleWorkerId ?? undefined,
      notes: this.notes || undefined,
    };

    const billId = this.selectedBill!.id;
    this.lastSubmittedBill = {
      id: billId,
      billNumber: this.selectedBill!.billNumber,
      customerName: this.selectedBill!.customerName,
    };
    this.billReturnService.create(billId, payload).subscribe({
      next: () => {
        this.submitting = false;
        this.submitted = true;
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.submitting = false;
        this.errorMsg = err?.error?.message ?? 'Failed to submit return.';
        this.cdr.markForCheck();
      },
    });
  }
}