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
import { MatSelectModule } from '@angular/material/select';
import { SelectionModel } from '@angular/cdk/collections';
import { StockService, SummaryLoadBill, IndividualReductionPending } from '../../../core/services/stock';
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
    MatSelectModule,
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
  stockBalance: Record<number, number> = {};
  saving = false;
  successMessage = '';

  // History
  loadBillHistory: SummaryLoadBill[] = [];
  historyColumns: string[] = ['id', 'loadDate', 'numberOfBills', 'status', 'createdByName', 'createdAt', 'actions', 'expand'];
  approvingId: number | null = null;
  expandedLoadId: number | null = null;

  // Admin: inline item edit on PENDING loads
  editingLoadItemId: number | null = null;
  editingLoadItemQty: number | null = null;
  addingItemToLoadId: number | null = null;
  newItemProductId: number | null = null;
  newItemQty: number = 1;

  // Individual reduction pending approvals (admin only)
  pendingReductions: IndividualReductionPending[] = [];
  pendingReductionsLoading = false;
  expandedReductionId: number | null = null;
  reductionColumns: string[] = ['billNumber', 'customerName', 'submittedByName', 'submittedAt', 'notes', 'actions'];
  rejectingId: number | null = null;

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

  get isAccountant(): boolean {
    return ['ACCOUNTANT', 'MAIN_ACCOUNTANT'].includes(this.auth.getRole() ?? '');
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
    if (this.isAdmin || this.isAccountant) { this.loadPendingReductions(); }

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
    this.stockService.getShadowStockBalance().subscribe({
      next: (bal: Record<number, number>) => { this.stockBalance = bal; this.cdr.detectChanges(); },
      error: () => {},
    });
  }

  stockQty(productId: number): number {
    return this.stockBalance[productId] ?? 0;
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

  toggleLoadDetail(id: number): void {
    this.expandedLoadId = this.expandedLoadId === id ? null : id;
    this.cdr.detectChanges();
  }

  loadPendingReductions(): void {
    this.pendingReductionsLoading = true;
    this.stockService.getPendingIndividualReductions().subscribe({
      next: list => {
        this.pendingReductions = list;
        this.pendingReductionsLoading = false;
        this.cdr.detectChanges();
      },
      error: () => { this.pendingReductionsLoading = false; this.cdr.detectChanges(); },
    });
  }

  toggleReductionDetail(id: number): void {
    this.expandedReductionId = this.expandedReductionId === id ? null : id;
    this.cdr.detectChanges();
  }

  approveReduction(id: number): void {
    this.approvingId = id;
    this.stockService.approveIndividualReduction(id).subscribe({
      next: () => {
        this.approvingId = null;
        this.loadPendingReductions();
      },
      error: () => { this.approvingId = null; this.cdr.detectChanges(); },
    });
  }

  rejectReduction(id: number): void {
    const reason = prompt('Rejection reason (optional):') ?? '';
    this.rejectingId = id;
    this.stockService.rejectIndividualReduction(id, reason || undefined).subscribe({
      next: () => {
        this.rejectingId = null;
        this.loadPendingReductions();
      },
      error: () => { this.rejectingId = null; this.cdr.detectChanges(); },
    });
  }

  reductionItemsFor(r: IndividualReductionPending): IndividualReductionPending['items'] {
    return r.items ?? [];
  }

  reductionTotal(r: IndividualReductionPending): number {
    return (r.items ?? []).reduce((s, i) => s + i.lineTotal, 0);
  }

  loadItemsFor(load: SummaryLoadBill): any[] {
    return load.items ?? [];
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

  // Admin: edit items on PENDING summary loads
  startEditLoadItem(item: any): void {
    this.editingLoadItemId = item.id;
    this.editingLoadItemQty = item.quantity;
    this.cdr.detectChanges();
  }

  cancelLoadItemEdit(): void {
    this.editingLoadItemId = null;
    this.editingLoadItemQty = null;
    this.cdr.detectChanges();
  }

  saveLoadItemEdit(loadId: number, item: any): void {
    if (!this.editingLoadItemQty || this.editingLoadItemQty <= 0 || !item.id) return;
    this.stockService.updateSummaryLoadItemQty(loadId, item.id, this.editingLoadItemQty).subscribe({
      next: (updated) => {
        const idx = this.loadBillHistory.findIndex(l => l.id === loadId);
        if (idx >= 0) this.loadBillHistory[idx] = updated;
        this.editingLoadItemId = null;
        this.editingLoadItemQty = null;
        this.cdr.detectChanges();
      },
      error: () => this.cdr.detectChanges(),
    });
  }

  deleteLoadItem(loadId: number, item: any): void {
    if (!item.id || !confirm(`Delete ${item.productName} (qty ${item.quantity})?`)) return;
    this.stockService.deleteSummaryLoadItem(loadId, item.id).subscribe({
      next: () => {
        const idx = this.loadBillHistory.findIndex(l => l.id === loadId);
        if (idx >= 0 && this.loadBillHistory[idx].items) {
          this.loadBillHistory[idx] = {
            ...this.loadBillHistory[idx],
            items: this.loadBillHistory[idx].items!.filter(i => i.id !== item.id),
          };
        }
        this.cdr.detectChanges();
      },
      error: () => this.cdr.detectChanges(),
    });
  }

  toggleAddItem(loadId: number): void {
    this.addingItemToLoadId = this.addingItemToLoadId === loadId ? null : loadId;
    this.newItemProductId = null;
    this.newItemQty = 1;
    this.cdr.detectChanges();
  }

  submitAddItem(loadId: number): void {
    if (!this.newItemProductId || this.newItemQty < 1) return;
    this.stockService.addSummaryLoadItem(loadId, this.newItemProductId, this.newItemQty).subscribe({
      next: (updated) => {
        const idx = this.loadBillHistory.findIndex(l => l.id === loadId);
        if (idx >= 0) this.loadBillHistory[idx] = updated;
        this.addingItemToLoadId = null;
        this.newItemProductId = null;
        this.newItemQty = 1;
        this.cdr.detectChanges();
      },
      error: () => this.cdr.detectChanges(),
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
