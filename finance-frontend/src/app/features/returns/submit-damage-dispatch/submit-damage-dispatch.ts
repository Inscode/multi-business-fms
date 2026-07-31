import { Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { DamageDispatchService, DamageStockItem } from '../../../core/services/damage-dispatch';
import { localDateStr } from '../../../core/utils/date-utils';

interface SelectedItem {
  stock: DamageStockItem;
  qty: number;
}

@Component({
  selector: 'app-submit-damage-dispatch',
  standalone: true,
  imports: [
    CommonModule, FormsModule, DecimalPipe,
    MatButtonModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatIconModule, MatProgressSpinnerModule,
    MatDatepickerModule, MatNativeDateModule,
  ],
  templateUrl: './submit-damage-dispatch.html',
  styleUrl: './submit-damage-dispatch.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SubmitDamageDispatch implements OnInit {
  businesses = ['RAINCO'];
  selectedBusiness = 'RAINCO';
  dispatchDate: Date = new Date();
  notes = '';
  predictedValue: number | null = null;

  readonly MARGIN_DISCOUNT = 8.54;

  stockItems: DamageStockItem[] = [];
  loadingStock = false;

  selected: SelectedItem[] = [];

  saving = false;
  errorMsg = '';
  successMsg = '';

  constructor(private service: DamageDispatchService, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void { this.loadStock(); }

  loadStock(): void {
    this.loadingStock = true;
    this.selected = [];
    this.service.getDamageStock(this.selectedBusiness).subscribe({
      next: (items) => {
        this.stockItems = items;
        this.loadingStock = false;
        this.cdr.markForCheck();
      },
      error: () => { this.loadingStock = false; this.cdr.markForCheck(); },
    });
  }

  isSelected(item: DamageStockItem): boolean {
    return this.selected.some(s => s.stock.productId === item.productId);
  }

  toggleItem(item: DamageStockItem): void {
    const idx = this.selected.findIndex(s => s.stock.productId === item.productId);
    if (idx >= 0) {
      this.selected.splice(idx, 1);
    } else {
      this.selected.push({ stock: item, qty: 1 });
    }
    this.cdr.markForCheck();
  }

  getQty(item: DamageStockItem): number {
    return this.selected.find(s => s.stock.productId === item.productId)?.qty ?? 1;
  }

  setQty(item: DamageStockItem, qty: number): void {
    const entry = this.selected.find(s => s.stock.productId === item.productId);
    if (entry) entry.qty = Math.min(Math.max(1, qty), item.damageQty);
  }

  get grossTotal(): number {
    return this.selected.reduce((sum, s) => sum + s.stock.unitPrice * s.qty, 0);
  }

  get afterMargin(): number {
    return this.grossTotal * (1 - this.MARGIN_DISCOUNT / 100);
  }

  get canSubmit(): boolean {
    return this.selected.length > 0 && !this.saving;
  }

  submit(): void {
    if (!this.canSubmit) return;
    this.saving = true;
    this.errorMsg = '';
    this.successMsg = '';

    this.service.create({
      business: this.selectedBusiness,
      dispatchDate: localDateStr(this.dispatchDate),
      notes: this.notes.trim() || undefined,
      predictedValue: this.predictedValue ?? undefined,
      items: this.selected.map(s => ({ productId: s.stock.productId, quantity: s.qty })),
    }).subscribe({
      next: (res) => {
        this.saving = false;
        this.selected = [];
        this.notes = '';
        this.predictedValue = null;
        this.successMsg = `Dispatch #${res.id} recorded — Rs ${res.totalValue.toLocaleString()} total value.`;
        this.loadStock();
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.saving = false;
        this.errorMsg = err?.error?.message ?? 'Failed to record dispatch.';
        this.cdr.markForCheck();
      },
    });
  }
}
