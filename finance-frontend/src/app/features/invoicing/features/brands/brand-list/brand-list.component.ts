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
import { BrandService } from '../../../core/services/brand.service';
import { Brand, CategoryType } from '../../../core/models/models';
import { Auth } from '../../../../../core/services/auth';

@Component({
  selector: 'app-brand-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, ReactiveFormsModule,
            MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
            MatSelectModule, MatProgressSpinnerModule, MatTooltipModule],
  templateUrl: './brand-list.component.html',
  styleUrl: './brand-list.component.scss'
})
export class BrandListComponent implements OnInit {
  private svc  = inject(BrandService);
  cdr  = inject(ChangeDetectorRef);
  private auth = inject(Auth);

  get isAdmin(): boolean { return this.auth.getRole() === 'ADMIN'; }
  private fb   = inject(FormBuilder);

  brands:   Brand[] = [];
  loading   = true;
  category: CategoryType | '' = '';
  showForm  = false;
  saving    = false;
  editingId: number | null = null;
  expandedId: number | null = null;

  form = this.fb.group({
    name:             ['', Validators.required],
    brandCode:        [''],
    category:         ['RAINCO' as CategoryType, Validators.required],
    discountType:     ['SLAB', Validators.required],
    defaultMarginPct: [null as number | null],
    active:           [true]
  });

  ngOnInit() { this.load(); }

  get filtered() {
    return this.brands.filter(b => !this.category || b.category === this.category);
  }

  load() {
    this.loading = true;
    this.svc.list().pipe(catchError(() => of([]))).subscribe(data => {
      this.brands = data; this.loading = false; this.cdr.markForCheck();
    });
  }

  openNew() {
    this.editingId = null;
    this.form.reset({ category: 'RAINCO', discountType: 'SLAB', active: true });
    this.showForm = true;
    this.expandedId = null;
    this.cdr.markForCheck();
  }

  openEdit(b: Brand) {
    this.editingId = b.id;
    this.form.patchValue({
      name:             b.name,
      brandCode:        b.brandCode ?? '',
      category:         b.category,
      discountType:     b.discountType,
      defaultMarginPct: b.defaultMarginPct ?? null,
      active:           b.active
    });
    this.showForm = true;
    this.expandedId = null;
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

  toggleSlabs(id: number) {
    this.expandedId = this.expandedId === id ? null : id;
    this.cdr.markForCheck();
  }

  categoryClass(c: string) {
    return ({ RAINCO: 'success', STATIONERY: 'warn', PLASTIC: 'info' } as any)[c] ?? 'info';
  }

  discountTypeLabel(t: string) {
    return ({ SLAB: 'Slab', FIXED_PCT: 'Fixed %', MANUAL: 'Manual', NONE: 'None' } as any)[t] ?? t;
  }
}
