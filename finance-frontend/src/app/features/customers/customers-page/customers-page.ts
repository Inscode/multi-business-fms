import { CommonModule } from '@angular/common';
import { DeliveryService } from '../../../core/services/delivery';
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
import { CustomerHealth, CustomerHealthService } from '../../../core/services/customer-health';
import { CustomerHealthDialog } from '../../../shared/customer-health-dialog/customer-health-dialog';
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

  displayedColumns = ['name', 'phone', 'area', 'tier', 'shopType', 'health', 'status', 'actions'];

  /**
   * Ratings for the business being looked at, keyed by customer id.
   *
   * <p>Loaded for the whole list in one request rather than per row: a page of two
   * hundred customers would otherwise fire two hundred calls, and the rating is only
   * worth showing if it is there before anyone scrolls past it.
   */
  healthByCustomer = new Map<number, CustomerHealth>();
  /** Which business the column is rating. A customer good on one can be poor on another. */
  healthBusiness = 'RAINCO';
  readonly healthBusinesses = ['RAINCO', 'STATIONERY', 'PLASTIC'];
  loadingHealth = false;

  loadHealth(): void {
    this.loadingHealth = true;
    this.healthService.getForBusiness(this.healthBusiness).subscribe({
      next: (list) => {
        this.healthByCustomer = new Map(
          list.filter(h => h.customerId != null).map(h => [h.customerId!, h]));
        this.loadingHealth = false;
        this.cdr.markForCheck();
      },
      error: () => {
        // A missing rating leaves the column blank; it is not worth an error banner over
        // a page whose main job is editing customers.
        this.healthByCustomer = new Map();
        this.loadingHealth = false;
        this.cdr.markForCheck();
      },
    });
  }

  onHealthBusinessChange(business: string): void {
    this.healthBusiness = business;
    this.loadHealth();
  }

  /** The rating for the chosen business, or null when they have never bought from it. */
  ratingFor(c: CustomerResponse): string | null {
    const h = this.healthByCustomer.get(c.id);
    if (!h) return null;
    const biz = h.businesses.find(b => b.business === this.healthBusiness);
    return biz ? biz.rating : null;
  }

  /** The first reason behind the rating, as the row's tooltip. */
  ratingHint(c: CustomerResponse): string {
    const h = this.healthByCustomer.get(c.id);
    const biz = h?.businesses.find(b => b.business === this.healthBusiness);
    if (!biz) return 'No ' + this.healthBusiness.toLowerCase() + ' bills for this customer.';
    return biz.reasons.length ? biz.reasons.join(' ') : 'Nothing against them.';
  }

  openHealth(c: CustomerResponse): void {
    this.dialog.open(CustomerHealthDialog, {
      data: { customerId: c.id, customerName: c.name },
      width: '760px',
      maxWidth: '95vw',
    });
  }

  /**
   * The same areas a lorry round is opened for.
   *
   * <p>Read from the route list rather than held here, so a customer's area and a
   * round's name come from one vocabulary. Two lists would drift, and a round whose
   * name does not match its customers cannot be counted against them.
   *
   * <p>The built-in list stands in only if the call fails, so the form still works
   * offline rather than offering an empty dropdown.
   */
  areas: string[] = [
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
    private healthService: CustomerHealthService,
  ) {
    this.form = this.fb.group({
      name:     ['', [Validators.required, Validators.minLength(2)]],
      phone:    ['', Validators.required],
      area:     [null, Validators.required],
      tier:     [null],
      shopType: [null],
    });
  }

  ngOnInit(): void {
    this.load();
    this.loadHealth();
  }

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
