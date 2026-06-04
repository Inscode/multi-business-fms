import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatChipsModule } from '@angular/material/chips';
import { CustomerService, CustomerResponse } from '../../../core/services/customer';
import { Auth } from '../../../core/services/auth';

@Component({
  selector: 'app-customers-page',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatChipsModule,
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

  displayedColumns = ['name', 'status', 'actions'];

  constructor(
    private customerService: CustomerService,
    private fb: FormBuilder,
    private auth: Auth,
    private cdr: ChangeDetectorRef,
  ) {
    this.form = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(2)]],
    });
  }

  ngOnInit(): void {
    this.load();
  }

  get canDelete(): boolean {
    return this.auth.getRole() === 'ADMIN';
  }

  load(): void {
    this.loading = true;
    this.customerService.getAll().subscribe({
      next: (data) => {
        this.customers = data;
        this.applyFilter();
        this.loading = false;
        this.cdr.detectChanges();
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
      ? this.customers.filter(c => c.name.toLowerCase().includes(q))
      : [...this.customers];
  }

  onSearch(event: Event): void {
    this.searchQuery = (event.target as HTMLInputElement).value;
    this.applyFilter();
  }

  openAdd(): void {
    this.editingId = null;
    this.form.reset({ name: '' });
    this.showForm = true;
    this.errorMsg = '';
  }

  openEdit(c: CustomerResponse): void {
    this.editingId = c.id;
    this.form.setValue({ name: c.name });
    this.showForm = true;
    this.errorMsg = '';
  }

  cancelForm(): void {
    this.showForm = false;
    this.editingId = null;
    this.form.reset();
  }

  save(): void {
    if (this.form.invalid) return;
    this.saving = true;
    this.errorMsg = '';
    const req = { name: this.form.value.name };

    const call = this.editingId
      ? this.customerService.update(this.editingId, req)
      : this.customerService.create(req);

    call.subscribe({
      next: () => {
        this.saving = false;
        this.cancelForm();
        this.load();
      },
      error: (err) => {
        this.saving = false;
        this.errorMsg = err?.error?.message ?? 'Failed to save customer';
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
    if (!confirm(`Delete "${c.name}"? This cannot be undone.`)) return;
    this.customerService.delete(c.id).subscribe({ next: () => this.load(), error: () => {} });
  }
}
