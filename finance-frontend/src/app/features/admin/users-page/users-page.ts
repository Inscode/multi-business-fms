import { CommonModule, LowerCasePipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { UserService, UserResponse } from '../../../core/services/user';
import { Worker as WorkerApi, WorkerResponse } from '../../../core/services/worker';

@Component({
  selector: 'app-users-page',
  templateUrl: './users-page.html',
  styleUrl: './users-page.scss',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    LowerCasePipe,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatMenuModule,
    MatDividerModule,
  ],
})
export class UsersPage implements OnInit {
  users: UserResponse[] = [];
  filteredUsers: UserResponse[] = [];
  workers: WorkerResponse[] = [];
  loading = true;
  error = false;
  formLoading = false;
  formError = '';
  showPanel = false;
  editingUser: UserResponse | null = null;

  searchQuery = '';
  selectedStatus = 'active';

  displayedColumns = ['fullName', 'username', 'role', 'status', 'actions'];

  readonly roles = [
    { value: 'WORKER',          label: 'Delivery' },
    { value: 'ACCOUNTANT',      label: 'Accountant' },
    { value: 'MAIN_ACCOUNTANT', label: 'Main Accountant' },
    { value: 'SHOP_ACCOUNTANT', label: 'Shop Accountant' },
    { value: 'OWNER',           label: 'Owner' },
  ];

  form: FormGroup;

  get panelTitle(): string {
    return this.editingUser ? 'Edit User' : 'Add User';
  }

  roleLabel(value: string): string {
    return this.roles.find(r => r.value === value)?.label ?? value;
  }

  get selectedRole(): string { return this.form.get('role')?.value ?? ''; }

  constructor(
    private fb: FormBuilder,
    private userService: UserService,
    private workerService: WorkerApi,
    private cdr: ChangeDetectorRef
  ) {
    this.form = this.fb.group({
      fullName: ['', Validators.required],
      username: ['', Validators.required],
      password: ['', Validators.required],
      role:     [null, Validators.required],
      workerId: [null],
    });
  }

  ngOnInit(): void {
    this.load();
    this.workerService.getAllWorkers().subscribe({
      next: (list: WorkerResponse[]) => { this.workers = list.filter(w => w.active); this.cdr.detectChanges(); },
      error: () => {},
    });
  }

  load(): void {
    this.loading = true;
    this.cdr.detectChanges();
    this.userService.getAll().subscribe({
      next: (u) => {
        this.users = u;
        this.loading = false;
        this.applyFilters();
        this.cdr.detectChanges();
      },
      error: () => {
        this.error = true;
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  applyFilters(): void {
    const query = this.searchQuery.toLowerCase().trim();
    this.filteredUsers = this.users.filter(u => {
      const matchesSearch = !query ||
        u.fullName.toLowerCase().includes(query) ||
        u.username.toLowerCase().includes(query);
      const matchesStatus =
        this.selectedStatus === 'active'   ?  u.active :
        this.selectedStatus === 'inactive' ? !u.active : true;
      return matchesSearch && matchesStatus;
    });
    this.cdr.detectChanges();
  }

  onSearchChange(): void { this.applyFilters(); }
  onStatusChange(): void { this.applyFilters(); }

  openAddPanel(): void {
    this.editingUser = null;
    this.form.reset();
    this.form.get('password')!.setValidators(Validators.required);
    this.form.get('password')!.updateValueAndValidity();
    this.formError = '';
    this.showPanel = true;
    this.cdr.detectChanges();
  }

  openEditPanel(user: UserResponse): void {
    this.editingUser = user;
    this.form.patchValue({
      fullName: user.fullName,
      username: user.username,
      password: '',
      role:     user.role,
    });
    this.form.get('password')!.clearValidators();
    this.form.get('password')!.updateValueAndValidity();
    this.formError = '';
    this.showPanel = true;
    this.cdr.detectChanges();
  }

  closePanel(): void {
    this.showPanel = false;
    this.editingUser = null;
    this.form.reset();
    this.cdr.detectChanges();
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.formLoading = true;
    this.formError = '';
    this.cdr.detectChanges();

    const { fullName, username, password, role, workerId } = this.form.value;

    const updatePayload = password?.trim()
      ? { fullName, username, role, password }
      : { fullName, username, role };

    const request$ = this.editingUser
      ? this.userService.update(this.editingUser.id, updatePayload)
      : this.userService.create({ fullName, username, password, role, ...(workerId ? { workerId } : {}) });

    request$.subscribe({
      next: () => {
        this.formLoading = false;
        this.closePanel();
        this.load();
      },
      error: (e) => {
        this.formError = e?.error?.message ?? 'Failed to save. Please try again.';
        this.formLoading = false;
        this.cdr.detectChanges();
      }
    });
  }

  deactivate(user: UserResponse): void {
    this.userService.deactivate(user.id).subscribe({
      next: () => this.load(),
      error: () => alert('Failed to deactivate user.')
    });
  }
}