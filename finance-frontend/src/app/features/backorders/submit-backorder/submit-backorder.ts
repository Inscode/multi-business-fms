import { Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormControl } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatTooltipModule } from '@angular/material/tooltip';
import { BackorderBillOption, BackorderService } from '../../../core/services/backorder';
import { StockService } from '../../../core/services/stock';

interface ReturnProduct {
  id: number;
  name: string;
  unitPrice: number;
  business: string;
}

interface BackorderLine {
  productId: number;
  productName: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  amountToAdd: number;
}

@Component({
  selector: 'app-submit-backorder',
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
    MatIconModule,
    MatProgressSpinnerModule,
    MatAutocompleteModule,
    MatTooltipModule,
    DecimalPipe,
  ],
  templateUrl: './submit-backorder.html',
  styleUrl: './submit-backorder.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SubmitBackorder implements OnInit {
  // Bill selection
  availableBills: BackorderBillOption[] = [];
  selectedBill: BackorderBillOption | null = null;
  billSearchControl = new FormControl<BackorderBillOption | string | null>(null);
  filteredBills: BackorderBillOption[] = [];

  // Product search
  allProducts: ReturnProduct[] = [];
  filteredProducts: ReturnProduct[] = [];
  productSearchControl = new FormControl<ReturnProduct | string | null>(null);
  productQtyControl = new FormControl<number | null>(null);
  amountToAddControl = new FormControl<number | null>(0);

  // Line items
  lineItems: BackorderLine[] = [];
  notes = '';

  saving = false;
  successMessage = '';
  errorMessage = '';

  itemColumns: string[] = ['product', 'quantity', 'unitPrice', 'lineTotal', 'amountToAdd', 'actions'];

  get canAdd(): boolean {
    const product = this.selectedProduct;
    const qty = this.productQtyControl.value;
    return !!product && !!qty && qty > 0;
  }

  get selectedProduct(): ReturnProduct | null {
    const val = this.productSearchControl.value;
    return val && typeof val === 'object' ? val as ReturnProduct : null;
  }

  get totalLineTotal(): number {
    return this.lineItems.reduce((sum, i) => sum + i.lineTotal, 0);
  }

  get totalAmountToAdd(): number {
    return this.lineItems.reduce((sum, i) => sum + i.amountToAdd, 0);
  }

  constructor(
    private backorderService: BackorderService,
    private stockService: StockService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.loadBills();
    this.loadProducts();

    this.billSearchControl.valueChanges.subscribe(val => {
      if (typeof val === 'string') {
        const s = val.toLowerCase();
        this.filteredBills = s
          ? this.availableBills.filter(b =>
              b.billNumber.toLowerCase().includes(s) ||
              b.customerName.toLowerCase().includes(s))
          : this.availableBills.slice(0, 20);
      } else if (val && typeof val === 'object') {
        this.selectedBill = val as BackorderBillOption;
      } else {
        this.filteredBills = this.availableBills.slice(0, 20);
      }
      this.cdr.detectChanges();
    });

    this.productSearchControl.valueChanges.subscribe(val => {
      if (typeof val === 'string') {
        const s = val.toLowerCase();
        this.filteredProducts = s
          ? this.allProducts.filter(p => p.name.toLowerCase().includes(s))
          : [];
      } else {
        this.filteredProducts = [];
      }
      this.cdr.detectChanges();
    });
  }

  private loadBills(): void {
    this.backorderService.getActiveBills().subscribe({
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
      next: (products: any[]) => {
        this.allProducts = products.filter((p: any) => p.business === 'RAINCO');
        this.cdr.detectChanges();
      },
      error: err => console.error('Failed to load products:', err),
    });
  }

  displayBill = (bill: BackorderBillOption | null): string =>
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

    const amountToAdd = this.amountToAddControl.value ?? 0;

    this.lineItems = [
      ...this.lineItems,
      {
        productId: product.id,
        productName: product.name,
        quantity: qty,
        unitPrice: product.unitPrice,
        lineTotal: qty * product.unitPrice,
        amountToAdd,
      },
    ];

    this.productSearchControl.setValue(null, { emitEvent: false });
    this.productQtyControl.setValue(null);
    this.amountToAddControl.setValue(0);
    this.filteredProducts = [];
    this.cdr.detectChanges();
  }

  removeLineItem(index: number): void {
    this.lineItems = this.lineItems.filter((_, i) => i !== index);
    this.cdr.detectChanges();
  }

  submit(): void {
    if (!this.selectedBill || this.lineItems.length === 0) return;

    this.saving = true;
    this.successMessage = '';
    this.errorMessage = '';

    this.backorderService.submit({
      billId: this.selectedBill.id,
      items: this.lineItems.map(i => ({
        productId: i.productId,
        quantity: i.quantity,
        amountToAdd: i.amountToAdd,
      })),
      notes: this.notes || undefined,
    }).subscribe({
      next: () => {
        this.successMessage = `Backorder submitted for ${this.selectedBill!.billNumber} — ${this.lineItems.length} item(s) pending admin approval.`;
        this.saving = false;
        this.notes = '';
        this.clearBill();
        this.loadBills();
        this.cdr.detectChanges();
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? 'Failed to submit backorder. Please try again.';
        this.saving = false;
        this.cdr.detectChanges();
      },
    });
  }

  reset(): void {
    this.clearBill();
    this.notes = '';
    this.loadBills();
  }
}
