import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ReturnProductResponse, ReturnProductService } from '../../../core/services/return-product';
import { StockService } from '../../../core/services/stock';

@Component({
  selector: 'app-return-products',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    DecimalPipe,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
  ],
  templateUrl: './return-products.html',
  styleUrl: './return-products.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReturnProductsPage implements OnInit {
  @Input() readonly = false;

  products: ReturnProductResponse[] = [];
  damageBalance: Record<number, number> = {};
  loading = true;
  error = false;
  showInactive = false;

  showAddForm = false;
  adding = false;
  newName = '';
  newPrice = 0;

  editingId: number | null = null;
  editName = '';
  editPrice = 0;

  editingQtyId: number | null = null;
  editQtyValue = 0;

  get displayed(): ReturnProductResponse[] {
    return this.showInactive
      ? this.products
      : this.products.filter(p => p.isReturnProduct);
  }

  constructor(
    private returnProductService: ReturnProductService,
    private stockService: StockService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.load();
    this.stockService.getDamageStockBalance().subscribe({
      next: (bal) => { this.damageBalance = bal; this.cdr.detectChanges(); },
      error: () => {},
    });
  }

  load(): void {
    this.loading = true;
    this.error = false;
    this.returnProductService.getAll().subscribe({
      next: (p) => {
        this.products = p;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.error = true;
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  damageQty(id: number): number {
    return this.damageBalance[id] ?? 0;
  }

  startAdd(): void {
    this.showAddForm = true;
    this.newName = '';
    this.newPrice = 0;
    this.cdr.detectChanges();
  }

  cancelAdd(): void {
    this.showAddForm = false;
    this.cdr.detectChanges();
  }

  saveAdd(): void {
    if (!this.newName || this.newPrice <= 0) return;
    this.adding = true;
    this.returnProductService.create({ business: 'RAINCO', name: this.newName, unitPrice: this.newPrice }).subscribe({
      next: () => {
        this.adding = false;
        this.showAddForm = false;
        this.load();
      },
      error: () => {
        this.adding = false;
        this.cdr.detectChanges();
      },
    });
  }

  startEdit(p: ReturnProductResponse): void {
    this.editingId = p.id;
    this.editName = p.name;
    this.editPrice = p.unitPrice;
    this.cdr.detectChanges();
  }

  cancelEdit(): void {
    this.editingId = null;
    this.cdr.detectChanges();
  }

  saveEdit(id: number): void {
    if (!this.editName || this.editPrice <= 0) return;
    this.returnProductService.update(id, { business: 'RAINCO', name: this.editName, unitPrice: this.editPrice }).subscribe({
      next: () => {
        this.editingId = null;
        this.load();
      },
      error: () => alert('Failed to update product.'),
    });
  }

  startEditQty(p: ReturnProductResponse): void {
    this.editingQtyId = p.id;
    this.editQtyValue = this.damageQty(p.id);
    this.cdr.detectChanges();
  }

  cancelEditQty(): void {
    this.editingQtyId = null;
    this.cdr.detectChanges();
  }

  saveEditQty(id: number): void {
    if (this.editQtyValue < 0) return;
    this.returnProductService.setDamageQty(id, this.editQtyValue).subscribe({
      next: () => {
        this.damageBalance[id] = this.editQtyValue;
        this.editingQtyId = null;
        this.cdr.detectChanges();
      },
      error: (err) => {
        alert(err?.error?.message ?? 'Failed to update qty.');
        this.cdr.detectChanges();
      },
    });
  }

  toggle(p: ReturnProductResponse): void {
    const action = p.isReturnProduct
      ? this.returnProductService.deactivate(p.id)
      : this.returnProductService.activate(p.id);
    action.subscribe({
      next: () => this.load(),
      error: () => alert('Failed to update.'),
    });
  }
}
