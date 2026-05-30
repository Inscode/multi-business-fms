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
import { Bill, BillResponse } from '../../../core/services/bill';
import { BillReturnService, BillReturnItemRequest } from '../../../core/services/bill-return';
import { ReturnProductService, ReturnProductResponse } from '../../../core/services/return-product';
import { Worker, WorkerResponse } from '../../../core/services/worker';

interface LineItem {
  productId?: number;
  itemName: string;
  unitPrice: number;
  quantityRequested: number;
  quantityReturned?: number;
  lineTotal: number;
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
  bills: BillResponse[] = [];
  filteredBills: BillResponse[] = [];
  selectedBill: BillResponse | null = null;
  products: ReturnProductResponse[] = [];
  workers: WorkerResponse[] = [];

  searchQuery = '';
  productSearch = '';
  filteredProducts: ReturnProductResponse[] = [];
  showProductDropdown = false;

  returnType = '';
  items: LineItem[] = [];
  discountPercentage: number | null = null;
  discountFixed: number | null = null;
  predictedValue: number | null = null;
  responsibleWorkerId: number | null = null;
  notes = '';

  loading = false;
  loadingBills = true;
  submitting = false;
  errorMsg = '';

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

  get itemsTotal(): number {
    return this.items.reduce((s, i) => s + i.lineTotal, 0);
  }

  get calculatedAmount(): number {
    let amount = this.itemsTotal;
    if (this.discountPercentage && this.discountPercentage > 0) {
      amount -= (amount * this.discountPercentage) / 100;
    }
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
    private workerService: Worker,
    private router: Router,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.billService.getBills().subscribe({
      next: (b) => {
        this.bills = b.filter(bill => bill.status !== 'COMPLETED');
        this.filteredBills = this.bills;
        this.loadingBills = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.loadingBills = false;
        this.cdr.detectChanges();
      },
    });
    this.workerService.getAllWorkers().subscribe({
      next: (w: WorkerResponse[]) => { this.workers = w; this.cdr.detectChanges(); },
      error: () => {},
    });
  }

  filterBills(): void {
    const q = this.searchQuery.toLowerCase().trim();
    this.filteredBills = q
      ? this.bills.filter(b =>
          b.billNumber.toLowerCase().includes(q) ||
          b.customerName.toLowerCase().includes(q)
        )
      : this.bills;
    this.cdr.detectChanges();
  }

  selectBill(bill: BillResponse): void {
    this.selectedBill = bill;
    this.items = [];
    this.products = [];
    this.returnType = '';
    this.discountPercentage = null;
    this.discountFixed = null;
    this.predictedValue = null;
    this.searchQuery = `${bill.billNumber} — ${bill.customerName}`;
    this.filteredBills = [];

    if (bill.business !== 'PLASTIC') {
      this.returnProductService.getByBusiness(bill.business).subscribe({
        next: (p) => { this.products = p; this.cdr.detectChanges(); },
        error: () => {},
      });
    }
    this.cdr.detectChanges();
  }

  clearBill(): void {
    this.selectedBill = null;
    this.searchQuery = '';
    this.items = [];
    this.products = [];
    this.filteredProducts = [];
    this.productSearch = '';
    this.showProductDropdown = false;
    this.returnType = '';
    this.filteredBills = this.bills;
    this.cdr.detectChanges();
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
      existing.lineTotal = existing.unitPrice * existing.quantityRequested;
    } else {
      this.items.push({
        productId: product.id,
        itemName: product.name,
        unitPrice: product.unitPrice,
        quantityRequested: 1,
        lineTotal: product.unitPrice,
      });
    }
    this.cdr.detectChanges();
  }

  addManualItem(): void {
    this.items.push({
      itemName: '',
      unitPrice: 0,
      quantityRequested: 1,
      lineTotal: 0,
    });
    this.cdr.detectChanges();
  }

  updateLineTotal(item: LineItem): void {
    item.lineTotal = item.unitPrice * item.quantityRequested;
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
      items: this.items.map(i => ({
        productId: i.productId ?? undefined,
        itemName: i.productId ? undefined : i.itemName,
        unitPrice: i.productId ? undefined : i.unitPrice,
        quantityRequested: i.quantityRequested,
        quantityReturned: i.quantityReturned ?? undefined,
      } as BillReturnItemRequest)),
      discountPercentage: this.discountPercentage ?? undefined,
      discountFixed: this.discountFixed ?? undefined,
      predictedValue: this.predictedValue ?? undefined,
      responsibleWorkerId: this.responsibleWorkerId ?? undefined,
      notes: this.notes || undefined,
    };

    this.billReturnService.create(this.selectedBill!.id, payload).subscribe({
      next: () => {
        this.submitting = false;
        this.router.navigate(['/bills', this.selectedBill!.id]);
      },
      error: (err) => {
        this.submitting = false;
        this.errorMsg = err?.error?.message ?? 'Failed to submit return.';
        this.cdr.detectChanges();
      },
    });
  }
}