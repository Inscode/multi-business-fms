import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { Worker, WorkerResponse } from '../../../core/services/worker';
import { Auth } from '../../../core/services/auth';
import { A } from '@angular/cdk/keycodes';

@Component({
  selector: 'app-staff-page',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatProgressSpinnerModule,
    MatMenuModule,
    MatDividerModule,
  ],
  templateUrl: './staff-page.html',
  styleUrl: './staff-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StaffPage implements OnInit{
  allStaff: WorkerResponse[] = [];
  filteredStaff: WorkerResponse[] = [];
  loading = true;
  error = false;
  formLoading = false;
  formError = '';
  showPanel = false;
  editingStaff: WorkerResponse | null = null;

  searchQuery = '';
  selectedType = '';
  selectedStatus = 'active';

  staffTypes = ['DELIVERY', 'SALES_REP', 'SHOP'];
  displayedColumns = ['fullName', 'workerType', 'joinedDate', 'status', 'actions'];

  form: FormGroup;

  get isAdmin(): boolean { return this.auth.getRole() === 'ADMIN'; }
  get panelTitle(): string {
    return this.editingStaff ? 'Edit Staff Member' : 'Add Staff Member';
  }

  get canViewSalary(): boolean {
    return this.auth.getRole() === 'ADMIN' ||
    this.auth.getRole() === 'MAIN_ACCOUNTANT';
  }
  
  private filterByRole(staff: WorkerResponse[]): WorkerResponse[] {
    const role  =this.auth.getRole();
    switch(role) {
      case 'ACCOUNTANT':
      case 'MAIN_ACCOUNTANT':
        return staff.filter(s => 
          s.workerType === 'DELIVERY' || s.workerType === 'SALES_REP');
      default:
        return staff;
    }
  }

  typeLabel(type: string): string {
    const map: Record<string, string> = {
      DELIVERY:  'Delivery',
      SALES_REP: 'Sales Rep',
      SHOP:      'Shop'
    };
    return map[type] ?? type;
  }

  constructor(
    private fb: FormBuilder,
    private workerService: Worker,
    private auth: Auth,
    private cdr: ChangeDetectorRef
  ) {
    this.form = this.fb.group({
      fullName: ['', Validators.required],
      workerType: [null, Validators.required],
      baseSalary: [null, [Validators.required, Validators.min(1)]],
      joinedDate : [new Date(), Validators.required],
      notes: [''],
    });
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.cdr.detectChanges();

    this.workerService.getAllWorkers().subscribe({
      next: (w) => {
        this.allStaff = w;
        this.loading = false;
        this.applyFilters();
        this.cdr.detectChanges();
      },
      error: () => {
        this.error = true;
        this.loading = false;
        this.cdr.detectChanges();
      }
    })
  }

  applyFilters(): void {
     const query = this.searchQuery.toLowerCase().trim();

    this.filteredStaff = this.allStaff.filter(s => {
      const matchesSearch = !query ||
        s.fullName.toLowerCase().includes(query);
      const matchesType = !this.selectedType ||
        s.workerType === this.selectedType;
      const matchesStatus =
        this.selectedStatus === 'active'   ?  s.active :
        this.selectedStatus === 'inactive' ? !s.active : true;

      return matchesSearch && matchesType && matchesStatus;
    });

    this.cdr.detectChanges();
  }

    onSearchChange(): void  { this.applyFilters(); }
    onTypeChange(): void    { this.applyFilters(); }
    onStatusChange(): void  { this.applyFilters(); }


     openAddPanel(): void {
    this.editingStaff = null;
    this.form.reset({ joinedDate: new Date() });
    this.formError = '';
    this.showPanel = true;
    this.cdr.detectChanges();
  }

  openEditPanel(staff: WorkerResponse): void {
    this.editingStaff = staff;
    this.form.patchValue({
      fullName:   staff.fullName,
      workerType: staff.workerType,
      baseSalary: staff.baseSalary,
      joinedDate: new Date(staff.joinedDate),
      notes:      staff.notes,
    });
    this.formError = '';
    this.showPanel = true;
    this.cdr.detectChanges();
  }

  closePanel(): void {
    this.showPanel    = false;
    this.editingStaff = null;
    this.form.reset();
    this.cdr.detectChanges();
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.formLoading = true;
    this.formError   = '';
    this.cdr.detectChanges();

    const payload = this.form.value;

    const request$ = this.editingStaff
      ? this.workerService.updateWorker(this.editingStaff.id, payload)
      : this.workerService.createWorker(payload);

    request$.subscribe({
      next: () => {
        this.formLoading = false;
        this.closePanel();
        this.load();
      },
      error: (e) => {
        this.formError   = e?.error?.message ?? 'Failed to save. Please try again.';
        this.formLoading = false;
        this.cdr.detectChanges();
      }
    });
  }

  toggleActive(staff: WorkerResponse): void {
    const request$ = staff.active
      ? this.workerService.deactivateWorker(staff.id)
      : this.workerService.activateWorker(staff.id);

    request$.subscribe({
      next: () => this.load(),
      error: () => alert('Failed to update staff status.')
    });
  }




}
