import { Component, ChangeDetectionStrategy, ChangeDetectorRef, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { catchError, of } from 'rxjs';
import { ItemService } from '../../../core/services/item.service';
import { BrandService } from '../../../core/services/brand.service';
import { Item, Brand, CategoryType } from '../../../core/models/models';
import { Auth } from '../../../../../core/services/auth';
import { EditRequestService } from '../../../../../core/services/edit-request';

@Component({
  selector: 'app-item-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, RouterLink,
            MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
            MatSelectModule, MatProgressSpinnerModule, MatTooltipModule],
  templateUrl: './item-list.component.html',
  styleUrl: './item-list.component.scss'
})
export class ItemListComponent implements OnInit {
  private svc   = inject(ItemService);
  private bsvc  = inject(BrandService);
  private cdr   = inject(ChangeDetectorRef);
  private auth  = inject(Auth);
  private editRequests = inject(EditRequestService);

  get isAdmin(): boolean { return this.auth.getRole() === 'ADMIN'; }
  private fb    = inject(FormBuilder);

  items:    Item[]  = [];
  brands:   Brand[] = [];
  loading   = true;
  search    = '';
  category: CategoryType | '' = '';
  statusFilter: 'ALL' | 'ACTIVE' | 'INACTIVE' = 'ACTIVE';
  togglingId: number | null = null;
  showForm  = false;
  saving    = false;
  editingId: number | null = null;
  requestTarget: Item | null = null;
  formError = '';
  requestNotice = '';

  adjustingItem: Item | null = null;
  stockDelta = 0;
  stockNote  = '';
  adjusting  = false;

  form = this.fb.group({
    itemCode:       ['', Validators.required],
    description:    ['', Validators.required],
    category:       ['STATIONERY' as CategoryType, Validators.required],
    brandId:        [null as number | null, Validators.required],
    mrp:            [null as number | null],
    marginPct:      [null as number | null],
    wholesalePrice: [null as number | null],
    costPrice:      [null as number | null],
    stockQty:       [0, [Validators.required, Validators.min(0)]],
    requestReason:  [''],
    active:         [true]
  });

  ngOnInit() {
    this.bsvc.list().pipe(catchError(() => of([]))).subscribe(b => {
      this.brands = b; this.cdr.markForCheck();
    });
    this.load();
  }

  get filtered() {
    return this.items.filter(i =>
      (!this.search || i.itemCode.toLowerCase().includes(this.search.toLowerCase())
                    || i.description.toLowerCase().includes(this.search.toLowerCase()))
      && (!this.category || i.category === this.category)
      && (this.statusFilter === 'ALL'
          || (this.statusFilter === 'ACTIVE' ? i.active : !i.active))
    );
  }

  get inactiveCount(): number { return this.items.filter(i => !i.active).length; }

  setStatusFilter(f: 'ALL' | 'ACTIVE' | 'INACTIVE'): void {
    this.statusFilter = f;
    this.cdr.markForCheck();
  }

  /** Retire an item, or bring a retired one back into circulation. */
  toggleActive(item: Item): void {
    this.togglingId = item.id;
    this.cdr.markForCheck();
    this.svc.toggleActive(item.id).pipe(catchError(() => of(null))).subscribe(updated => {
      this.togglingId = null;
      if (updated) {
        const i = this.items.findIndex(x => x.id === updated.id);
        if (i >= 0) this.items[i] = updated;
        this.items = [...this.items];
      }
      this.cdr.markForCheck();
    });
  }

  load() {
    this.loading = true;
    // includeInactive: this screen is where retired items are found and reactivated
    this.svc.list(undefined, true).pipe(catchError(() => of([]))).subscribe(data => {
      this.items = data; this.loading = false; this.cdr.markForCheck();
    });
  }

  openNew() {
    this.enableAllFormFields();
    this.editingId = null;
    this.requestTarget = null;
    this.formError = '';
    this.requestNotice = '';
    this.setReasonRequired(!this.isAdmin);
    this.form.reset({ category: 'STATIONERY', active: true, stockQty: 0 });
    this.showForm = true;
    this.adjustingItem = null;
    this.cdr.markForCheck();
  }

  openEdit(i: Item) {
    this.enableAllFormFields();
    this.editingId = i.id;
    this.requestTarget = this.isAdmin ? null : i;
    this.formError = '';
    this.setReasonRequired(!this.isAdmin);
    this.form.patchValue({
      itemCode:       i.itemCode,
      description:    i.description,
      category:       i.category,
      brandId:        i.brandId,
      mrp:            i.mrp ?? null,
      marginPct:      i.marginPct ?? null,
      wholesalePrice: i.wholesalePrice ?? null,
      costPrice:      i.costPrice ?? null,
      stockQty:       i.stockQty,
      active:         i.active
    });
    if (!this.isAdmin) {
      this.form.get('itemCode')?.disable();
      this.form.get('category')?.disable();
      this.form.get('brandId')?.disable();
    }
    this.showForm = true;
    this.adjustingItem = null;
    this.cdr.markForCheck();
  }

  cancel() {
    this.showForm = false;
    this.editingId = null;
    this.requestTarget = null;
    this.formError = '';
    this.enableAllFormFields();
    this.cdr.markForCheck();
  }

  save() {
    if (this.form.invalid) return;
    this.formError = '';
    if (!this.isAdmin) {
      this.submitItemRequest();
      return;
    }
    this.saving = true;
    const raw = this.form.getRawValue();
    const req = {
      itemCode: raw.itemCode,
      description: raw.description,
      category: raw.category,
      brandId: raw.brandId,
      mrp: raw.mrp,
      marginPct: raw.marginPct,
      wholesalePrice: raw.wholesalePrice,
      costPrice: raw.costPrice,
      active: raw.active,
    };
    const obs = this.editingId ? this.svc.update(this.editingId, req) : this.svc.create(req);
    obs.pipe(catchError(() => of(null))).subscribe(res => {
      this.saving = false;
      if (res) { this.showForm = false; this.editingId = null; this.load(); }
      else this.formError = 'Could not save the item. Please check the details and try again.';
      this.cdr.markForCheck();
    });
  }

  private submitItemRequest(): void {
    const raw = this.form.getRawValue();
    const stockQty = Number(raw.stockQty);
    if (!Number.isInteger(stockQty) || stockQty < 0) {
      this.formError = 'Quantity must be a whole number greater than or equal to zero.';
      return;
    }

    const nullablePrice = (value: unknown): number | null =>
      value == null || value === '' ? null : Number(value);
    const proposed: Record<string, unknown> = this.requestTarget
      ? {
          description: String(raw.description ?? '').trim(),
          mrp: nullablePrice(raw.mrp),
          marginPct: nullablePrice(raw.marginPct),
          wholesalePrice: nullablePrice(raw.wholesalePrice),
          stockQty,
        }
      : {
          itemCode: String(raw.itemCode ?? '').trim(),
          description: String(raw.description ?? '').trim(),
          category: raw.category,
          brandId: Number(raw.brandId),
          mrp: nullablePrice(raw.mrp),
          marginPct: nullablePrice(raw.marginPct),
          wholesalePrice: nullablePrice(raw.wholesalePrice),
          stockQty,
        };

    const changes: Record<string, unknown> = {};
    if (this.requestTarget) {
      for (const [key, value] of Object.entries(proposed)) {
        const current = (this.requestTarget as any)[key];
        const equal = typeof value === 'number' ? Number(current) === value : (current ?? null) === value;
        if (!equal) changes[key] = value;
      }
      if (Object.keys(changes).length === 0) {
        this.formError = 'Change at least one item value before sending the request.';
        this.cdr.markForCheck();
        return;
      }
    } else {
      Object.assign(changes, proposed);
    }

    this.saving = true;
    this.editRequests.create({
      type: 'ITEM',
      targetId: this.requestTarget?.id ?? 0,
      targetRef: this.requestTarget?.itemCode ?? `NEW ITEM: ${String(raw.itemCode ?? '').trim()}`,
      requestedChanges: JSON.stringify(changes),
      reason: String(raw.requestReason ?? '').trim(),
    }).pipe(catchError(() => of(null))).subscribe(result => {
      this.saving = false;
      if (result) {
        this.showForm = false;
        this.editingId = null;
        this.requestTarget = null;
        this.requestNotice = 'Item change request sent to the admin for review.';
        this.enableAllFormFields();
      } else {
        this.formError = 'Could not send the request. Please try again.';
      }
      this.cdr.markForCheck();
    });
  }

  private setReasonRequired(required: boolean): void {
    const control = this.form.get('requestReason');
    if (!control) return;
    control.setValidators(required ? [Validators.required] : []);
    control.updateValueAndValidity({ emitEvent: false });
  }

  private enableAllFormFields(): void {
    Object.values(this.form.controls).forEach(control => control.enable({ emitEvent: false }));
  }

  openAdjust(i: Item) {
    this.adjustingItem = i;
    this.stockDelta = 0;
    this.stockNote  = '';
    this.showForm   = false;
    this.cdr.markForCheck();
  }

  cancelAdjust() { this.adjustingItem = null; this.cdr.markForCheck(); }

  doAdjust() {
    if (!this.adjustingItem || this.stockDelta === 0) return;
    this.adjusting = true;
    this.svc.adjustStock({ itemId: this.adjustingItem.id, delta: this.stockDelta, notes: this.stockNote || undefined })
      .pipe(catchError(() => of(null)))
      .subscribe(res => {
        this.adjusting = false;
        if (res) { this.adjustingItem = null; this.load(); }
        this.cdr.markForCheck();
      });
  }

  brandName(brandId: number) {
    return this.brands.find(b => b.id === brandId)?.name ?? '—';
  }

  categoryLabel(c: string) {
    return ({ RAINCO: 'Rainco', STATIONERY: 'Stationery', PLASTIC: 'Plastic' } as any)[c] ?? c;
  }

  categoryClass(c: string) {
    return ({ RAINCO: 'success', STATIONERY: 'warn', PLASTIC: 'info' } as any)[c] ?? 'info';
  }
}
