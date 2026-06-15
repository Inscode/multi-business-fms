import { Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormControl } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatTooltipModule } from '@angular/material/tooltip';
import { StockBillListItem, StockService } from '../../../core/services/stock';

type BillOption = StockBillListItem;

interface ReturnProduct {
  id: number;
  name: string;
  unitPrice: number;
  business: string;
}

interface LineItem {
  productId: number;
  productName: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

@Component({
  selector: 'app-stock-item-entry',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatTableModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatAutocompleteModule,
    MatChipsModule,
    MatDividerModule,
    MatTooltipModule,
    DecimalPipe,
  ],
  templateUrl: './stock-item-entry.html',
  styleUrl: './stock-item-entry.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StockItemEntryComponent implements OnInit {
  // Step 1 — Bill selection
  availableBills: BillOption[] = [];
  selectedBill: BillOption | null = null;
  billSearchControl = new FormControl<BillOption | string | null>(null);
  filteredBills: BillOption[] = [];

  // Step 2 — Line items
  allProducts: ReturnProduct[] = [];
  filteredProducts: ReturnProduct[] = [];
  lineItems: LineItem[] = [];
  productSearchControl = new FormControl<ReturnProduct | string | null>(null);
  productQtyControl = new FormControl<number | null>(null);

  saving = false;
  successMessage = '';
  errorMessage = '';

  itemColumns: string[] = ['product', 'quantity', 'unitPrice', 'lineTotal', 'actions'];

  get canAdd(): boolean {
    const product = this.selectedProduct;
    const qty = this.productQtyControl.value;
    return !!product && !!qty && qty > 0;
  }

  get selectedProduct(): ReturnProduct | null {
    const val = this.productSearchControl.value;
    return val && typeof val === 'object' ? val as ReturnProduct : null;
  }

  get totalAmount(): number {
    return this.lineItems.reduce((sum, i) => sum + i.lineTotal, 0);
  }

  get isLinkingBill(): boolean {
    return !!this.selectedBill?.isLinkingBill;
  }

  constructor(private stockService: StockService, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.loadBills();
    this.loadProducts();

    this.billSearchControl.valueChanges.subscribe(val => {
      if (typeof val === 'string') {
        const s = val.toLowerCase();
        this.filteredBills = s
          ? this.availableBills.filter(b =>
              b.billNumber.toLowerCase().includes(s) ||
              b.customerName.toLowerCase().includes(s) ||
              b.billSource.toLowerCase().includes(s))
          : this.availableBills.slice(0, 20);
      } else if (val && typeof val === 'object') {
        this.selectedBill = val as BillOption;
      } else {
        this.filteredBills = this.availableBills.slice(0, 20);
      }
      this.cdr.detectChanges();
    });

    this.productSearchControl.valueChanges.subscribe(val => {
      if (typeof val === 'string') {
        const s = val.toLowerCase();
        this.filteredProducts = s
          ? this.allProducts.filter(p => p.name.toLowerCase().includes(s) && p.business === 'RAINCO')
          : [];
      } else {
        this.filteredProducts = [];
      }
      this.cdr.detectChanges();
    });
  }

  private loadBills(): void {
    this.stockService.getBillsNotYetReduced().subscribe({
      next: bills => {
        this.availableBills = bills;
        this.filteredBills = bills.slice(0, 20);
        this.cdr.detectChanges();
      },
      error: err => console.error('Failed to load bills:', err),
    });
  }

  private loadProducts(): void {
    this.stockService.getRaincoProducts().subscribe({
      next: products => { this.allProducts = products; this.cdr.detectChanges(); },
      error: err => console.error('Failed to load products:', err),
    });
  }

  displayBill = (bill: BillOption | null): string =>
    bill ? `${bill.billNumber} — ${bill.customerName}` : '';

  displayProduct = (p: ReturnProduct | null): string => p ? p.name : '';

  onBillSelected(event: any): void {
    this.selectedBill = event.option.value;
    this.lineItems = [];
    this.filteredBills = [];
    this.successMessage = '';
    this.errorMessage = '';
    this.cdr.detectChanges();
  }

  clearBill(): void {
    this.selectedBill = null;
    this.billSearchControl.setValue(null, { emitEvent: false });
    this.lineItems = [];
    this.successMessage = '';
    this.errorMessage = '';
    this.cdr.detectChanges();
  }

  addLineItem(): void {
    const product = this.selectedProduct;
    const qty = this.productQtyControl.value;
    if (!product || !qty || qty <= 0) return;

    this.lineItems = [
      ...this.lineItems,
      {
        productId: product.id,
        productName: product.name,
        quantity: qty,
        unitPrice: product.unitPrice,
        lineTotal: qty * product.unitPrice,
      },
    ];

    this.productSearchControl.setValue(null, { emitEvent: false });
    this.productQtyControl.setValue(null);
    this.filteredProducts = [];
    this.cdr.detectChanges();
  }

  removeLineItem(index: number): void {
    this.lineItems = this.lineItems.filter((_, i) => i !== index);
    this.cdr.detectChanges();
  }

  save(): void {
    if (!this.selectedBill || this.lineItems.length === 0) return;

    this.saving = true;
    this.successMessage = '';
    this.errorMessage = '';

    this.stockService.reduceStockForBill({
      billId: this.selectedBill.id,
      items: this.lineItems.map(i => ({ productId: i.productId, quantity: i.quantity })),
    }).subscribe({
      next: () => {
        this.successMessage = this.isLinkingBill
          ? `Reference items saved for ${this.selectedBill!.billNumber} — ${this.lineItems.length} product(s). No stock movement created.`
          : `Reduction request submitted for ${this.selectedBill!.billNumber} — awaiting admin approval.`;
        this.saving = false;
        this.clearBill();
        this.loadBills(); // refresh list
        this.cdr.detectChanges();
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? 'Failed to reduce stock. Please try again.';
        this.saving = false;
        this.cdr.detectChanges();
      },
    });
  }

  reset(): void {
    this.clearBill();
    this.loadBills();
  }
}
