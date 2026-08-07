import { Component, ChangeDetectionStrategy, ChangeDetectorRef, inject, OnInit } from '@angular/core';
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

@Component({
  selector: 'app-item-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, ReactiveFormsModule,
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
    this.editingId = null;
    this.form.reset({ category: 'STATIONERY', active: true });
    this.showForm = true;
    this.adjustingItem = null;
    this.cdr.markForCheck();
  }

  openEdit(i: Item) {
    this.editingId = i.id;
    this.form.patchValue({
      itemCode:       i.itemCode,
      description:    i.description,
      category:       i.category,
      brandId:        i.brandId,
      mrp:            i.mrp ?? null,
      marginPct:      i.marginPct ?? null,
      wholesalePrice: i.wholesalePrice ?? null,
      active:         i.active
    });
    this.showForm = true;
    this.adjustingItem = null;
    this.cdr.markForCheck();
  }

  cancel() { this.showForm = false; this.editingId = null; this.cdr.markForCheck(); }

  save() {
    if (this.form.invalid) return;
    this.saving = true;
    const req = this.form.value;
    const obs = this.editingId ? this.svc.update(this.editingId, req) : this.svc.create(req);
    obs.pipe(catchError(() => of(null))).subscribe(res => {
      this.saving = false;
      if (res) { this.showForm = false; this.editingId = null; this.load(); }
      this.cdr.markForCheck();
    });
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
