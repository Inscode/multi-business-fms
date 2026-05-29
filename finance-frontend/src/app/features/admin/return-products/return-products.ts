import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { ReturnProductResponse, ReturnProductService } from '../../../core/services/return-product';

interface EditState {
  name: string;
  unitPrice: number;
  business: string;
}

@Component({
  selector: 'app-return-products',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    DecimalPipe,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './return-products.html',
  styleUrl: './return-products.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReturnProductsPage implements OnInit {
  products: ReturnProductResponse[] = [];
  loading = true;
  error = false;

  businesses = ['RAINCO', 'RETAIL_SHOP', 'PLASTIC', 'HARDWARE', 'STATIONERY'];
  filterBusiness = '';

  showAddForm = false;
  adding = false;
  newProduct: EditState = { name: '', unitPrice: 0, business: '' };

  editingId: number | null = null;
  editState: EditState = { name: '', unitPrice: 0, business: '' };

  get displayed(): ReturnProductResponse[] {
    return this.filterBusiness
      ? this.products.filter(p => p.business === this.filterBusiness)
      : this.products;
  }

  constructor(
    private returnProductService: ReturnProductService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void { this.load(); }

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

  startAdd(): void {
    this.showAddForm = true;
    this.newProduct = { name: '', unitPrice: 0, business: '' };
    this.cdr.detectChanges();
  }

  cancelAdd(): void {
    this.showAddForm = false;
    this.cdr.detectChanges();
  }

  saveAdd(): void {
    if (!this.newProduct.name || !this.newProduct.business || this.newProduct.unitPrice <= 0) return;
    this.adding = true;
    this.returnProductService.create(this.newProduct).subscribe({
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

  startEdit(product: ReturnProductResponse): void {
    this.editingId = product.id;
    this.editState = { name: product.name, unitPrice: product.unitPrice, business: product.business };
    this.cdr.detectChanges();
  }

  cancelEdit(): void {
    this.editingId = null;
    this.cdr.detectChanges();
  }

  saveEdit(id: number): void {
    if (!this.editState.name || this.editState.unitPrice <= 0) return;
    this.returnProductService.update(id, this.editState).subscribe({
      next: () => {
        this.editingId = null;
        this.load();
      },
      error: () => alert('Failed to update product.'),
    });
  }

  toggle(product: ReturnProductResponse): void {
    const action = product.active
      ? this.returnProductService.deactivate(product.id)
      : this.returnProductService.activate(product.id);
    action.subscribe({
      next: () => this.load(),
      error: () => alert('Failed to update status.'),
    });
  }
}