import { Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormControl } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDividerModule } from '@angular/material/divider';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { SelectionModel } from '@angular/cdk/collections';
import { BillStockItem, StockService } from '../../../core/services/stock';

interface UnlinkedBill {
  id: number;
  billNumber: string;
  billSource: 'DRAFT' | 'MANUAL';
  customerName: string;
  amount: number;
  billDate: string;
  enteredByName: string;
  totalQty: number;
  items?: BillStockItem[];
}

interface SystemBillOption {
  id: number;
  billNumber: string;
  customerName: string;
  amount: number;
  billDate: string;
  totalQty: number;
  items?: BillStockItem[];
}

interface ItemComparisonRow {
  productId: number;
  productName: string;
  systemQty: number;
  selectedQty: number;
  diff: number; // selectedQty - systemQty
}

@Component({
  selector: 'app-end-of-month-linking',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ReactiveFormsModule, DecimalPipe,
    MatTableModule, MatCheckboxModule, MatButtonModule, MatCardModule,
    MatFormFieldModule, MatInputModule, MatSelectModule, MatIconModule,
    MatProgressSpinnerModule, MatTooltipModule, MatDividerModule, MatAutocompleteModule,
  ],
  templateUrl: './end-of-month-linking.html',
  styleUrl: './end-of-month-linking.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EndOfMonthLinkingComponent implements OnInit {

  // ── Unlinked draft/manual bills ───────────────────────────────
  unlinkedBills: UnlinkedBill[] = [];
  filteredUnlinked: UnlinkedBill[] = [];
  selection = new SelectionModel<UnlinkedBill>(true, []);
  searchText = '';
  filterSource: string = 'ALL';

  // ── System bill selection ─────────────────────────────────────
  availableSystemBills: SystemBillOption[] = [];
  filteredSystemBills: SystemBillOption[] = [];
  systemBillCtrl = new FormControl<SystemBillOption | string | null>(null);
  selectedSystemBill: SystemBillOption | null = null;
  notesCtrl = new FormControl('');

  // ── State ─────────────────────────────────────────────────────
  saving = false;
  successMessage = '';
  errorMessage = '';
  itemComparison: ItemComparisonRow[] = [];

  displayedColumns = ['select', 'billNumber', 'billSource', 'customerName', 'amount', 'totalQty', 'billDate', 'enteredByName'];
  comparisonColumns = ['productName', 'systemQty', 'selectedQty', 'diff'];

  // ── Computed totals ───────────────────────────────────────────
  get selectedTotalAmount(): number {
    return this.selection.selected.reduce((s, b) => s + b.amount, 0);
  }
  get selectedTotalQty(): number {
    return this.selection.selected.reduce((s, b) => s + (b.totalQty || 0), 0);
  }
  get systemBillAmount(): number { return this.selectedSystemBill?.amount ?? 0; }
  get systemBillQty(): number    { return this.selectedSystemBill?.totalQty ?? 0; }

  get amountDiff(): number { return this.selectedTotalAmount - this.systemBillAmount; }
  get qtyDiff(): number    { return this.selectedTotalQty - this.systemBillQty; }

  get amountMatch(): boolean { return Math.abs(this.amountDiff) < 0.01; }
  get qtyMatch(): boolean    { return this.itemComparison.every(r => r.diff === 0); }
  get hasItemData(): boolean {
    return !!(this.selectedSystemBill?.items?.length || this.selection.selected.some(b => b.items?.length));
  }

  get canLink(): boolean {
    return this.selection.selected.length > 0 && !!this.selectedSystemBill && !this.saving;
  }

  get isAllSelected(): boolean {
    return this.selection.selected.length > 0 &&
           this.selection.selected.length === this.filteredUnlinked.length;
  }

  constructor(private stockService: StockService, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.loadUnlinked();
    this.loadSystemBills();

    this.systemBillCtrl.valueChanges.subscribe(val => {
      if (typeof val === 'string') {
        const s = val.toLowerCase();
        this.filteredSystemBills = s
          ? this.availableSystemBills.filter(b =>
              b.billNumber.toLowerCase().includes(s) ||
              b.customerName.toLowerCase().includes(s))
          : this.availableSystemBills.slice(0, 20);
        this.selectedSystemBill = null;
      } else if (val && typeof val === 'object') {
        this.selectedSystemBill = val as SystemBillOption;
        this.filteredSystemBills = [];
      } else {
        this.selectedSystemBill = null;
        this.filteredSystemBills = this.availableSystemBills.slice(0, 20);
      }
      this.cdr.detectChanges();
    });
  }

  private loadUnlinked(): void {
    this.stockService.getUnlinkedBills().subscribe({
      next: bills => {
        this.unlinkedBills = bills as UnlinkedBill[];
        this.applyFilter();
        this.cdr.detectChanges();
      },
      error: err => console.error('Failed to load unlinked bills:', err),
    });
  }

  private loadSystemBills(): void {
    this.stockService.getAvailableSystemBillsForLinking().subscribe({
      next: bills => {
        this.availableSystemBills = bills as SystemBillOption[];
        this.filteredSystemBills = bills.slice(0, 20) as SystemBillOption[];
        this.cdr.detectChanges();
      },
      error: err => console.error('Failed to load system bills:', err),
    });
  }

  applyFilter(): void {
    const s = this.searchText.toLowerCase();
    this.filteredUnlinked = this.unlinkedBills.filter(b => {
      const matchSrc = this.filterSource === 'ALL' || b.billSource === this.filterSource;
      const matchTxt = !s || b.billNumber.toLowerCase().includes(s) || b.customerName.toLowerCase().includes(s);
      return matchSrc && matchTxt;
    });
    this.cdr.detectChanges();
  }

  masterToggle(): void {
    this.isAllSelected
      ? this.selection.clear()
      : this.filteredUnlinked.forEach(r => this.selection.select(r));
    this.rebuildComparison();
    this.cdr.detectChanges();
  }

  toggleRow(row: UnlinkedBill, checked: boolean): void {
    checked ? this.selection.select(row) : this.selection.deselect(row);
    this.rebuildComparison();
    this.cdr.detectChanges();
  }

  rebuildComparison(): void {
    if (!this.selectedSystemBill) {
      this.itemComparison = [];
      return;
    }
    const sysItems = this.selectedSystemBill.items ?? [];
    const nameMap = new Map<number, string>();
    const sysQty = new Map<number, number>();
    for (const i of sysItems) {
      nameMap.set(i.productId, i.productName);
      sysQty.set(i.productId, (sysQty.get(i.productId) ?? 0) + i.quantity);
    }
    const selQty = new Map<number, number>();
    for (const bill of this.selection.selected) {
      for (const i of (bill.items ?? [])) {
        nameMap.set(i.productId, i.productName);
        selQty.set(i.productId, (selQty.get(i.productId) ?? 0) + i.quantity);
      }
    }
    const allIds = new Set([...sysQty.keys(), ...selQty.keys()]);
    this.itemComparison = [...allIds].map(pid => ({
      productId: pid,
      productName: nameMap.get(pid) ?? `Product ${pid}`,
      systemQty: sysQty.get(pid) ?? 0,
      selectedQty: selQty.get(pid) ?? 0,
      diff: (selQty.get(pid) ?? 0) - (sysQty.get(pid) ?? 0),
    })).sort((a, b) => a.productName.localeCompare(b.productName));
  }

  displaySystemBill = (b: SystemBillOption | null): string =>
    b ? `${b.billNumber} — ${b.customerName}` : '';

  onSystemBillSelected(event: any): void {
    this.selectedSystemBill = event.option.value;
    this.filteredSystemBills = [];
    this.successMessage = '';
    this.errorMessage = '';
    this.rebuildComparison();
    this.cdr.detectChanges();
  }

  clearSystemBill(): void {
    this.selectedSystemBill = null;
    this.systemBillCtrl.setValue(null, { emitEvent: false });
    this.itemComparison = [];
    this.cdr.detectChanges();
  }

  link(): void {
    if (!this.canLink) return;

    this.saving = true;
    this.successMessage = '';
    this.errorMessage = '';

    this.stockService.linkBills({
      draftManualBillIds: this.selection.selected.map(b => b.id),
      systemBillId: this.selectedSystemBill!.id,
      notes: this.notesCtrl.value ?? '',
    }).subscribe({
      next: () => {
        const nums = this.selection.selected.map(b => b.billNumber).join(', ');
        this.successMessage = `Linked ${this.selection.selected.length} bill(s) [${nums}] → ${this.selectedSystemBill!.billNumber}`;
        this.saving = false;
        this.selection.clear();
        this.clearSystemBill();
        this.notesCtrl.setValue('');
        this.loadUnlinked();
        this.loadSystemBills();
        this.cdr.detectChanges();
      },
      error: err => {
        this.errorMessage = err?.error?.message ?? 'Failed to link. Please try again.';
        this.saving = false;
        this.cdr.detectChanges();
      },
    });
  }

  reset(): void {
    this.selection.clear();
    this.clearSystemBill();
    this.notesCtrl.setValue('');
    this.searchText = '';
    this.filterSource = 'ALL';
    this.itemComparison = [];
    this.applyFilter();
    this.successMessage = '';
    this.errorMessage = '';
    this.cdr.detectChanges();
  }
}
