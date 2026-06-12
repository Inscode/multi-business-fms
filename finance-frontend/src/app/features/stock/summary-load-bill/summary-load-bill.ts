import { Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, FormControl, Validators } from '@angular/forms';
import { localDateStr } from '../../../core/utils/date-utils';
import { MatTableModule } from '@angular/material/table';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { SelectionModel } from '@angular/cdk/collections';
import { of, Observable } from 'rxjs';
import { StockService, SummaryLoadBill } from '../../../core/services/stock';
import { Auth } from '../../../core/services/auth';

interface SystemBill {
  id: number;
  billNumber: string;
  amount: number;
  enteredByName: string;
  paymentType: 'CASH' | 'CHEQUE';
  billDate: string;
}

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
  selector: 'app-summary-load-bill',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatTableModule,
    MatCheckboxModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatAutocompleteModule,
    MatTooltipModule,
    MatChipsModule,
    MatDividerModule,
    DecimalPipe,
  ],
  templateUrl: './summary-load-bill.html',
  styleUrl: './summary-load-bill.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SummaryLoadBillComponent implements OnInit {
  unassignedBills: SystemBill[] = [];
  selection = new SelectionModel<SystemBill>(true, []);
  loadForm: FormGroup;
  lineItems: LineItem[] = [];
  allProducts: ReturnProduct[] = [];
  filteredProducts: ReturnProduct[] = [];
  saving = false;
  successMessage = '';

  // History
  loadBillHistory: SummaryLoadBill[] = [];
  historyColumns: string[] = ['id', 'loadDate', 'numberOfBills', 'status', 'createdByName', 'createdAt', 'actions'];
  approvingId: number | null = null;

  // FormControls for product row
  productSearchControl = new FormControl<ReturnProduct | string | null>(null);
  productQtyControl = new FormControl<number | null>(null);

  displayedColumns: string[] = [
    'select',
    'billNumber',
    'amount',
    'enteredByName',
    'paymentType',
    'billDate',
  ];

  itemColumns: string[] = ['product', 'quantity', 'unitPrice', 'lineTotal', 'actions'];

  get isAllSelected(): boolean {
    return (
      this.selection.selected.length > 0 &&
      this.selection.selected.length === this.unassignedBills.length
    );
  }

  get selectedProduct(): ReturnProduct | null {
    const val = this.productSearchControl.value;
    return val && typeof val === 'object' ? val as ReturnProduct : null;
  }

  get isAdmin(): boolean {
    return this.auth.getRole() === 'ADMIN';
  }

  get canAdd(): boolean {
    return !!this.selectedProduct && !!this.productQtyControl.value && (this.productQtyControl.value > 0);
  }

  constructor(private fb: FormBuilder, private cdr: ChangeDetectorRef, private stockService: StockService, private auth: Auth) {
    this.loadForm = this.fb.group({
      loadDate: [localDateStr(), Validators.required],
      notes: [''],
    });
  }

  ngOnInit(): void {
    this.loadProducts();
    this.loadUnassignedBills();
    this.loadHistory();

    this.productSearchControl.valueChanges.subscribe(val => {
      if (typeof val === 'string') {
        const search = val.toLowerCase();
        this.filteredProducts = search
          ? this.allProducts.filter(p => p.name.toLowerCase().includes(search) && p.business === 'RAINCO')
          : [];
      } else {
        this.filteredProducts = [];
      }
      this.cdr.detectChanges();
    });
  }

  private loadProducts(): void {
    this.stockService.getRaincoProducts().subscribe({
      next: (products) => {
        this.allProducts = products;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Failed to load products:', err);
        this.successMessage = 'Error loading products';
        this.cdr.detectChanges();
      },
    });
  }

  private loadUnassignedBills(): void {
    this.stockService.getUnassignedSystemBills().subscribe({
      next: (bills) => {
        this.unassignedBills = bills;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Failed to load bills:', err);
        this.successMessage = 'Error loading system bills';
        this.cdr.detectChanges();
      },
    });
  }

  masterToggle(): void {
    this.isAllSelected
      ? this.selection.clear()
      : this.unassignedBills.forEach((row) => this.selection.select(row));
  }

  getTotalAmount(): number {
    return this.selection.selected.reduce((sum, bill) => sum + bill.amount, 0);
  }

  clearSelection(): void {
    this.selection.clear();
    this.lineItems = [...[]];
    this.successMessage = '';
    this.cdr.detectChanges();
  }

  private loadHistory(): void {
    this.stockService.getSummaryLoadBills().subscribe({
      next: (bills) => {
        this.loadBillHistory = bills;
        this.cdr.detectChanges();
      },
      error: (err) => console.error('Failed to load history:', err),
    });
  }

  approve(id: number): void {
    this.approvingId = id;
    this.stockService.approveSummaryLoadBill(id).subscribe({
      next: () => {
        this.approvingId = null;
        this.loadHistory();
      },
      error: (err) => {
        console.error('Failed to approve:', err);
        this.approvingId = null;
        this.cdr.detectChanges();
      },
    });
  }

  reject(id: number): void {
    this.approvingId = id;
    this.stockService.rejectSummaryLoadBill(id).subscribe({
      next: () => {
        this.approvingId = null;
        this.loadHistory();
      },
      error: (err) => {
        console.error('Failed to reject:', err);
        this.approvingId = null;
        this.cdr.detectChanges();
      },
    });
  }

  displayProduct = (product: ReturnProduct | null): string =>
    product ? product.name : '';

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

    // Reset via FormControl — Angular properly clears input and internal state
    this.productSearchControl.setValue(null, { emitEvent: false });
    this.productQtyControl.setValue(null);
    this.filteredProducts = [];
    this.cdr.detectChanges();
  }

  removeLineItem(index: number): void {
    this.lineItems = this.lineItems.filter((_, i) => i !== index);
    this.cdr.detectChanges();
  }

  saveLoadBill(): void {
    if (this.loadForm.invalid || this.selection.selected.length === 0 || this.lineItems.length === 0) {
      return;
    }

    this.saving = true;

    const request = {
      systemBillIds: this.selection.selected.map(bill => bill.id),
      items: this.lineItems,
      loadDate: this.loadForm.get('loadDate')?.value,
      notes: this.loadForm.get('notes')?.value,
    };

    this.stockService.createSummaryLoadBill(request).subscribe({
      next: () => {
        this.successMessage = `Summary Load Bill created with ${this.selection.selected.length} bills and ${this.lineItems.length} items`;
        this.saving = false;
        this.clearSelection();
        this.loadForm.reset({ loadDate: localDateStr() });
        this.loadUnassignedBills();
        this.loadHistory();
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Failed to save summary load bill:', err);
        this.successMessage = 'Error saving summary load bill. Please try again.';
        this.saving = false;
        this.cdr.detectChanges();
      },
    });
  }
}
