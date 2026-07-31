import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { CustomerService, CustomerResponse } from '../../../core/services/customer';
import { Auth } from '../../../core/services/auth';
import { ConfirmDialog } from '../../../shared/confirm-dialog/confirm-dialog';

@Component({
  selector: 'app-customers-page',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatChipsModule,
    MatDialogModule,
  ],
  templateUrl: './customers-page.html',
  styleUrl: './customers-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CustomersPage implements OnInit {
  customers: CustomerResponse[] = [];
  filtered: CustomerResponse[] = [];
  loading = false;
  errorMsg = '';

  showForm = false;
  editingId: number | null = null;
  form: FormGroup;
  saving = false;

  searchQuery = '';

  displayedColumns = ['name', 'phone', 'area', 'tier', 'shopType', 'status', 'actions'];

  areas = [
    'Ambagasdowa', 'Badalkumbura', 'Badulla', 'Bandarawela', 'Beragala',
    'Bogakumbura', 'Boralanda', 'Demodara', 'Diyatalawa', 'Ella',
    'Etampitiya', 'Haldummulla', 'Hali-Ela', 'Hasalaka', 'Haputale',
    'Hopton', 'Kandaketiya', 'Keppatipola', 'Kumbalwela', 'Lunugala',
    'Lunuwatta', 'Mahiyanganaya', 'Meegahakivula', 'Passara',
    'Uva-Paranagama', 'Welimada',
  ];

  tiers     = ['PLATINUM', 'GOLD', 'SILVER', 'BRONZE', 'STANDARD'];
  shopTypes = [
    'Cosmetic and Fancy Outlet',
    'Shoe Shop',
    'Bag Shop',
    'Textile and Fashion',
    'Book Shop and Communication',
    'Multi Outlet',
    'Mother/Baby Care Outlet',
    'Fancy Outlet',
    'Pooja Banda Outlet',
    'Other Outlet',
    'Super Market',
    'Grocery',
    'Hospitals',
    'Hotels and Restaurants',
  ];

  constructor(
    private customerService: CustomerService,
    private fb: FormBuilder,
    private auth: Auth,
    private cdr: ChangeDetectorRef,
    private dialog: MatDialog,
  ) {
    this.form = this.fb.group({
      name:     ['', [Validators.required, Validators.minLength(2)]],
      phone:    ['', Validators.required],
      area:     [null, Validators.required],
      tier:     [null],
      shopType: [null],
    });
  }

  ngOnInit(): void { this.load(); }

  get isAdmin(): boolean   { return this.auth.getRole() === 'ADMIN'; }
  get canDelete(): boolean { return this.isAdmin; }
  get canEdit(): boolean   { return this.isAdmin; }

  load(): void {
    this.loading = true;
    this.customerService.getAll().subscribe({
      next: (data) => {
        this.customers = data;
        this.loading = false;
        this.applyFilter(); // applyFilter calls detectChanges once at the end
      },
      error: () => {
        this.errorMsg = 'Failed to load customers';
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  applyFilter(): void {
    const q = this.searchQuery.toLowerCase().trim();
    this.filtered = q
      ? this.customers.filter(c =>
          c.name.toLowerCase().includes(q) ||
          (c.phone ?? '').includes(q) ||
          (c.area ?? '').toLowerCase().includes(q))
      : [...this.customers];
    this.cdr.detectChanges();
  }

  onSearch(event: Event): void {
    this.searchQuery = (event.target as HTMLInputElement).value;
    this.applyFilter();
  }

  openAdd(): void {
    this.editingId = null;
    this.form.reset();
    this.showForm = true;
    this.errorMsg = '';
    this.cdr.detectChanges();
  }

  openEdit(c: CustomerResponse): void {
    this.editingId = c.id;
    this.form.setValue({
      name:     c.name,
      phone:    c.phone    ?? '',
      area:     c.area     ?? null,
      tier:     c.tier     ?? null,
      shopType: c.shopType ?? null,
    });
    this.showForm = true;
    this.errorMsg = '';
    this.cdr.detectChanges();
  }

  cancelForm(): void {
    this.showForm = false;
    this.editingId = null;
    this.form.reset();
    this.cdr.detectChanges();
  }

  save(): void {
    if (this.form.invalid) return;
    this.saving = true;
    this.errorMsg = '';

    const req = {
      name:     this.form.value.name,
      phone:    this.form.value.phone    || undefined,
      area:     this.form.value.area     || undefined,
      tier:     this.form.value.tier     || undefined,
      shopType: this.form.value.shopType || undefined,
    };

    const call = this.editingId
      ? this.customerService.update(this.editingId, req)
      : this.customerService.create(req);

    call.subscribe({
      next: () => {
        this.saving = false;
        this.cdr.detectChanges();
        this.cancelForm();
        this.load();
      },
      error: (err) => {
        this.saving = false;
        this.errorMsg = err?.error?.message ?? 'Failed to save customer';
        this.cdr.detectChanges();
      },
    });
  }

  toggleActive(c: CustomerResponse): void {
    const call = c.active
      ? this.customerService.deactivate(c.id)
      : this.customerService.activate(c.id);
    call.subscribe({ next: () => this.load(), error: () => {} });
  }

  remove(c: CustomerResponse): void {
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Delete Customer',
        message: `Delete "${c.name}"? This cannot be undone.`,
        confirmText: 'Delete',
        confirmColor: 'warn',
      },
    }).afterClosed().subscribe(result => {
      if (!result?.confirmed) return;
      this.customerService.delete(c.id).subscribe({ next: () => this.load(), error: () => {} });
    });
  }
}
