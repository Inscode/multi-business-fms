import { Component, ChangeDetectionStrategy, ChangeDetectorRef, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { catchError, of } from 'rxjs';
import { InvoiceService } from '../../../core/services/invoice.service';
import { ItemService } from '../../../core/services/item.service';
import { CustomerService } from '../../../core/services/customer.service';
import { Item, Customer, InvoiceMethod, InvoiceType } from '../../../core/models/models';

interface LineEntry {
  item: Item;
  qty: number;
}

@Component({
  selector: 'app-invoice-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, ReactiveFormsModule,
            MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
            MatSelectModule, MatProgressSpinnerModule, MatTooltipModule, MatAutocompleteModule],
  templateUrl: './invoice-form.component.html',
  styleUrl: './invoice-form.component.scss'
})
export class InvoiceFormComponent implements OnInit {
  private svc      = inject(InvoiceService);
  private itemSvc  = inject(ItemService);
  private custSvc  = inject(CustomerService);
  private router   = inject(Router);
  cdr      = inject(ChangeDetectorRef);
  private fb       = inject(FormBuilder);

  items:     Item[]     = [];
  customers: Customer[] = [];
  lines:     LineEntry[] = [];
  loading    = false;
  saving     = false;
  error      = '';

  itemSearch    = '';
  filteredItems: Item[] = [];

  customerSearch     = '';
  filteredCustomers: Customer[] = [];

  today = new Date().toISOString().slice(0, 10);

  form = this.fb.group({
    method:              ['MIX' as InvoiceMethod, Validators.required],
    invoiceType:         ['CREDIT' as InvoiceType, Validators.required],
    customerId:          [null as number | null,   Validators.required],
    invoiceDate:         [this.today,              Validators.required],
    externalRef:         [''],
    agentPrintedNet:     [null as number | null],
    plasticDiscountPct:  [null as number | null],
    plasticDiscountAmount: [null as number | null]
  });

  ngOnInit() {
    this.loading = true;
    Promise.all([
      this.itemSvc.list().pipe(catchError(() => of([]))).toPromise(),
      this.custSvc.list().pipe(catchError(() => of([]))).toPromise()
    ]).then(([items, customers]) => {
      this.items     = items     ?? [];
      this.customers = customers ?? [];
      this.filteredItems = this.itemsForMethod();
      this.filteredCustomers = this.customers;
      this.loading = false;
      this.cdr.markForCheck();
    });

    this.form.get('method')!.valueChanges.subscribe(() => {
      this.lines = this.lines.filter(l => this.itemAllowed(l.item));
      this.itemSearch = '';
      this.filteredItems = this.itemsForMethod();
      this.cdr.markForCheck();
    });
  }

  private itemAllowed(item: Item): boolean {
    if (this.method === 'RAINCO_ONLY') return item.category === 'RAINCO';
    if (this.method === 'STATIONERY_ONLY') return item.category === 'STATIONERY';
    return true;
  }

  private itemsForMethod(): Item[] {
    return this.items.filter(i => this.itemAllowed(i));
  }

  filterCustomers(q: string | Customer) {
    if (typeof q !== 'string') return; // mat-autocomplete fires ngModelChange with the raw option value on selection
    this.customerSearch = q;
    if (!q) { this.filteredCustomers = this.customers; return; }
    const lq = q.toLowerCase();
    this.filteredCustomers = this.customers.filter(c =>
      c.name.toLowerCase().includes(lq) || (c.customerCode ?? '').toLowerCase().includes(lq)
    );
    this.cdr.markForCheck();
  }

  selectCustomer(c: Customer) {
    this.form.patchValue({ customerId: c.id });
    this.customerSearch = c.customerCode ? `${c.name} — ${c.customerCode}` : c.name;
    this.cdr.markForCheck();
  }

  customerDisplayFn = (c: Customer | string): string => typeof c === 'string' ? c : (c ? (c.customerCode ? `${c.name} — ${c.customerCode}` : c.name) : '');

  filterItems(q: string | Item) {
    if (typeof q !== 'string') return; // mat-autocomplete fires ngModelChange with the raw option value on selection
    this.itemSearch = q;
    const scoped = this.itemsForMethod();
    if (!q) { this.filteredItems = scoped; return; }
    const lq = q.toLowerCase();
    this.filteredItems = scoped.filter(i =>
      i.itemCode.toLowerCase().includes(lq) || i.description.toLowerCase().includes(lq)
    );
    this.cdr.markForCheck();
  }

  itemDisplayFn = (value: string | Item): string => typeof value === 'string' ? value : '';

  addLine(item: Item) {
    if (this.lines.find(l => l.item.id === item.id)) {
      const existing = this.lines.find(l => l.item.id === item.id)!;
      existing.qty++;
    } else {
      this.lines.push({ item, qty: 1 });
    }
    this.itemSearch = '';
    this.filteredItems = this.itemsForMethod();
    this.cdr.markForCheck();
  }

  removeLine(idx: number) {
    this.lines.splice(idx, 1);
    this.cdr.markForCheck();
  }

  lineWsp(line: LineEntry) {
    return line.item.wsp ?? line.item.wholesalePrice ?? 0;
  }

  lineTotal(line: LineEntry) {
    return this.lineWsp(line) * line.qty;
  }

  get grossTotal() {
    return this.lines.reduce((sum, l) => sum + this.lineTotal(l), 0);
  }

  get method(): InvoiceMethod { return this.form.value.method as InvoiceMethod; }

  get showPlastic() { return this.method === 'MIX' || this.method === 'RAINCO_ONLY'; }

  get showAgentRef() { return this.method !== 'MIX'; }

  submit() {
    if (this.form.invalid || this.lines.length === 0) return;
    this.saving = true;
    this.error  = '';
    const v = this.form.getRawValue();
    const req = {
      method:                v.method,
      invoiceType:           v.invoiceType,
      customerId:            v.customerId,
      invoiceDate:           v.invoiceDate,
      externalRef:           v.externalRef || undefined,
      agentPrintedNet:       v.agentPrintedNet || undefined,
      plasticDiscountPct:    v.plasticDiscountPct || undefined,
      plasticDiscountAmount: v.plasticDiscountAmount || undefined,
      lines: this.lines.map(l => ({ itemId: l.item.id, qty: l.qty }))
    };
    this.svc.create(req).pipe(catchError(err => {
      this.error = err?.error?.message ?? 'Failed to create invoice';
      this.saving = false;
      this.cdr.markForCheck();
      return of(null);
    })).subscribe(res => {
      this.saving = false;
      if (res) this.router.navigate(['/invoicing/invoices', res.id]);
      this.cdr.markForCheck();
    });
  }

  cancel() { this.router.navigate(['/invoicing/invoices']); }
}
