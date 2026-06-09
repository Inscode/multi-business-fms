import { Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormControl, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatDialogModule } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDividerModule } from '@angular/material/divider';
import { MatExpansionModule } from '@angular/material/expansion';
import { InventoryService, ProductStockBalance, StockInRequest } from '../../../core/services/inventory';
import { Auth } from '../../../core/services/auth';

interface LineItem { productId: number; productName: string; quantity: number; }

@Component({
  selector: 'app-inventory-tab',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ReactiveFormsModule, DecimalPipe,
    MatTabsModule, MatTableModule, MatButtonModule, MatCardModule,
    MatFormFieldModule, MatInputModule, MatIconModule,
    MatProgressSpinnerModule, MatAutocompleteModule, MatDialogModule,
    MatTooltipModule, MatDividerModule, MatExpansionModule,
  ],
  templateUrl: './inventory-tab.html',
  styleUrl: './inventory-tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InventoryTabComponent implements OnInit {

  // ── Auth ───────────────────────────────────────────────────────
  get isAdmin(): boolean { return this.auth.getRole() === 'ADMIN'; }

  // ── Stock Balances (Tab 1) ─────────────────────────────────────
  balances: ProductStockBalance[] = [];
  filteredBalances: ProductStockBalance[] = [];
  balanceSearch = '';
  loadingBalances = false;
  balanceColumns = ['productName','unitPrice','availableQty','damageQty','status'];

  // ── Qty adjustment (admin) ─────────────────────────────────────
  setQtyId: number | null = null;
  setQtyValue: number | null = null;
  togglingId: number | null = null;

  // ── Add Product form ───────────────────────────────────────────
  showProductForm = false;
  productForm: FormGroup;
  savingProduct = false;
  productMessage = '';
  editingProductId: number | null = null;

  // ── Add Stock / Opening Stock (Tab 2) ─────────────────────────
  stockInItems: LineItem[] = [];
  productSearchCtrl = new FormControl<ProductStockBalance | string | null>(null);
  qtyCtrl = new FormControl<number | null>(null);
  filteredProducts: ProductStockBalance[] = [];
  stockInForm: FormGroup;
  savingStockIn = false;
  stockInMessage = '';
  stockInError = '';
  stockInHistory: StockInRequest[] = [];
  stockInColumns = ['id','referenceNumber','stockDate','status','submittedByName','createdAt'];
  lineItemColumns = ['productName','quantity'];
  expandedRequest: number | null = null;

  // ── Approvals (Tab 3 — ADMIN only) ────────────────────────────
  pendingRequests: StockInRequest[] = [];
  approvingId: number | null = null;
  approvalMessage = '';

  get selectedProduct(): ProductStockBalance | null {
    const v = this.productSearchCtrl.value;
    return v && typeof v === 'object' ? v as ProductStockBalance : null;
  }
  get canAddItem(): boolean {
    return !!this.selectedProduct && !!this.qtyCtrl.value && this.qtyCtrl.value > 0;
  }

  constructor(
    private inventoryService: InventoryService,
    private auth: Auth,
    private fb: FormBuilder,
    private cdr: ChangeDetectorRef
  ) {
    this.productForm = this.fb.group({
      name: ['', Validators.required],
      unitPrice: [null, [Validators.required, Validators.min(0.01)]],
    });
    this.stockInForm = this.fb.group({
      referenceNumber: [''],
      stockDate: [new Date().toISOString().split('T')[0], Validators.required],
      notes: [''],
    });
  }

  ngOnInit(): void {
    this.loadBalances();
    this.loadStockInHistory();
    if (this.isAdmin) this.loadPending();

    this.productSearchCtrl.valueChanges.subscribe(v => {
      if (typeof v === 'string') {
        const s = v.toLowerCase();
        this.filteredProducts = s
          ? this.balances.filter(b => b.productName.toLowerCase().includes(s))
          : this.balances.slice(0, 15);
      } else {
        this.filteredProducts = [];
      }
      this.cdr.detectChanges();
    });
  }

  // ── Balances ───────────────────────────────────────────────────
  loadBalances(): void {
    this.loadingBalances = true;
    this.inventoryService.getBalances().subscribe({
      next: b => {
        this.balances = b;
        this.applyBalanceFilter();
        this.loadingBalances = false;
        this.cdr.detectChanges();
      },
      error: () => { this.loadingBalances = false; this.cdr.detectChanges(); },
    });
  }

  applyBalanceFilter(): void {
    const s = this.balanceSearch.toLowerCase();
    this.filteredBalances = this.balances
      .filter(b => this.isAdmin || b.active)
      .filter(b => !s || b.productName.toLowerCase().includes(s));
    this.cdr.detectChanges();
  }

  // ── Product CRUD ───────────────────────────────────────────────
  openAddProduct(): void {
    this.editingProductId = null;
    this.productForm.reset({ name: '', unitPrice: null });
    this.showProductForm = true;
    this.productMessage = '';
    this.cdr.detectChanges();
  }

  openEditProduct(p: ProductStockBalance): void {
    this.editingProductId = p.productId;
    this.productForm.setValue({ name: p.productName, unitPrice: p.unitPrice });
    this.showProductForm = true;
    this.productMessage = '';
    this.cdr.detectChanges();
  }

  deleteProduct(p: ProductStockBalance): void {
    if (!confirm(`Delete "${p.productName}"? This cannot be undone.\n\nNote: products with existing stock balance cannot be deleted.`)) return;
    this.inventoryService.deleteProduct(p.productId).subscribe({
      next: () => {
        this.productMessage = `"${p.productName}" deleted.`;
        this.showProductForm = false;
        this.loadBalances();
        this.cdr.detectChanges();
      },
      error: err => {
        this.productMessage = err?.error?.message ?? 'Cannot delete this product.';
        this.cdr.detectChanges();
      },
    });
  }

  saveProduct(): void {
    if (this.productForm.invalid) return;
    this.savingProduct = true;
    const req = this.productForm.value;
    const obs = this.editingProductId
      ? this.inventoryService.updateProduct(this.editingProductId, req)
      : this.inventoryService.createProduct(req);

    obs.subscribe({
      next: () => {
        this.productMessage = this.editingProductId ? 'Product updated.' : 'Product added.';
        this.savingProduct = false;
        this.showProductForm = false;
        this.loadBalances();
        this.cdr.detectChanges();
      },
      error: err => {
        this.productMessage = err?.error?.message ?? 'Failed to save product.';
        this.savingProduct = false;
        this.cdr.detectChanges();
      },
    });
  }

  // ── Admin: qty adjustment & active toggle ─────────────────────
  startSetQty(p: ProductStockBalance): void {
    this.setQtyId = p.productId;
    this.setQtyValue = p.availableQty;
    this.cdr.detectChanges();
  }

  cancelSetQty(): void {
    this.setQtyId = null;
    this.setQtyValue = null;
    this.cdr.detectChanges();
  }

  saveSetQty(p: ProductStockBalance): void {
    if (this.setQtyValue === null || this.setQtyValue < 0) return;
    this.inventoryService.setProductQty(p.productId, this.setQtyValue).subscribe({
      next: () => {
        this.setQtyId = null;
        this.setQtyValue = null;
        this.loadBalances();
      },
      error: err => {
        alert(err?.error?.message ?? 'Failed to update quantity.');
        this.cdr.detectChanges();
      },
    });
  }

  toggleActive(p: ProductStockBalance): void {
    if (this.togglingId !== null) return;
    const action = p.active
      ? this.inventoryService.deactivateProduct(p.productId)
      : this.inventoryService.activateProduct(p.productId);
    this.togglingId = p.productId;
    action.subscribe({
      next: () => { this.togglingId = null; this.loadBalances(); },
      error: err => {
        this.togglingId = null;
        alert(err?.error?.message ?? 'Failed to update product status.');
        this.cdr.detectChanges();
      },
    });
  }

  // ── Stock-in line items ────────────────────────────────────────
  displayProduct = (p: ProductStockBalance | null): string => p ? p.productName : '';

  addItem(): void {
    const p = this.selectedProduct;
    const qty = this.qtyCtrl.value;
    if (!p || !qty || qty <= 0) return;
    this.stockInItems = [...this.stockInItems, { productId: p.productId, productName: p.productName, quantity: qty }];
    this.productSearchCtrl.setValue(null, { emitEvent: false });
    this.qtyCtrl.setValue(null);
    this.filteredProducts = [];
    this.cdr.detectChanges();
  }

  removeItem(i: number): void {
    this.stockInItems = this.stockInItems.filter((_, idx) => idx !== i);
    this.cdr.detectChanges();
  }

  // ── Submit stock-in (ACC/MAIN_ACC) ────────────────────────────
  submitStockIn(): void {
    if (this.stockInForm.invalid || this.stockInItems.length === 0) return;
    this.savingStockIn = true;
    this.stockInMessage = '';
    this.stockInError = '';

    const req = {
      ...this.stockInForm.value,
      items: this.stockInItems.map(i => ({ productId: i.productId, quantity: i.quantity })),
    };

    this.inventoryService.submitStockIn(req).subscribe({
      next: () => {
        this.stockInMessage = 'Stock-in request submitted. Awaiting admin approval.';
        this.savingStockIn = false;
        this.stockInItems = [];
        this.stockInForm.reset({ stockDate: new Date().toISOString().split('T')[0] });
        this.loadStockInHistory();
        this.cdr.detectChanges();
      },
      error: err => {
        this.stockInError = err?.error?.message ?? 'Failed to submit.';
        this.savingStockIn = false;
        this.cdr.detectChanges();
      },
    });
  }

  // ── Opening stock (ADMIN direct) ──────────────────────────────
  addOpeningStock(): void {
    if (this.stockInForm.invalid || this.stockInItems.length === 0) return;
    this.savingStockIn = true;
    this.stockInMessage = '';
    this.stockInError = '';

    const req = {
      ...this.stockInForm.value,
      items: this.stockInItems.map(i => ({ productId: i.productId, quantity: i.quantity })),
    };

    this.inventoryService.addOpeningStock(req).subscribe({
      next: () => {
        this.stockInMessage = 'Opening stock added successfully.';
        this.savingStockIn = false;
        this.stockInItems = [];
        this.stockInForm.reset({ stockDate: new Date().toISOString().split('T')[0] });
        this.loadBalances();
        this.cdr.detectChanges();
      },
      error: err => {
        this.stockInError = err?.error?.message ?? 'Failed to add opening stock.';
        this.savingStockIn = false;
        this.cdr.detectChanges();
      },
    });
  }

  loadStockInHistory(): void {
    this.inventoryService.getAllStockIn().subscribe({
      next: r => { this.stockInHistory = r; this.cdr.detectChanges(); },
      error: () => {},
    });
  }

  // ── Approvals ──────────────────────────────────────────────────
  loadPending(): void {
    this.inventoryService.getPendingStockIn().subscribe({
      next: r => { this.pendingRequests = r; this.cdr.detectChanges(); },
      error: () => {},
    });
  }

  approve(id: number): void {
    this.approvingId = id;
    this.inventoryService.approveStockIn(id).subscribe({
      next: () => {
        this.approvingId = null;
        this.approvalMessage = 'Stock-in approved. Stock updated.';
        this.loadPending();
        this.loadBalances();
        this.loadStockInHistory();
        this.cdr.detectChanges();
      },
      error: err => {
        this.approvalMessage = err?.error?.message ?? 'Failed to approve.';
        this.approvingId = null;
        this.cdr.detectChanges();
      },
    });
  }

  reject(id: number): void {
    const reason = prompt('Rejection reason (optional):') ?? '';
    this.approvingId = id;
    this.inventoryService.rejectStockIn(id, reason).subscribe({
      next: () => {
        this.approvingId = null;
        this.approvalMessage = 'Request rejected.';
        this.loadPending();
        this.loadStockInHistory();
        this.cdr.detectChanges();
      },
      error: () => { this.approvingId = null; this.cdr.detectChanges(); },
    });
  }

  statusLabel(s: string): string {
    return ({ PENDING: 'Pending', APPROVED: 'Approved', REJECTED: 'Rejected' } as any)[s] ?? s;
  }
}
