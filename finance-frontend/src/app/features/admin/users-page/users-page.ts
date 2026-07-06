import { CommonModule, LowerCasePipe } from '@angular/common';
import { AbstractControl, ValidationErrors } from '@angular/forms';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
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

function passwordsMatchValidator(group: AbstractControl): ValidationErrors | null {
  const pw      = (group.get('password')?.value ?? '').trim();
  const confirm = (group.get('confirmPassword')?.value ?? '').trim();
  if (pw && confirm && pw !== confirm) {
    return { passwordMismatch: true };
  }
  return null;
}

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
export class UsersPage implements OnInit, OnDestroy {
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

  // Eye-toggle flags
  showNewPw      = false;
  showConfirmPw  = false;
  showAdminPw    = false;
  showCreatePw   = false;

  readonly roles = [
    { value: 'WORKER',          label: 'Delivery' },
    { value: 'ACCOUNTANT',      label: 'Accountant' },
    { value: 'MAIN_ACCOUNTANT', label: 'Main Accountant' },
    { value: 'SHOP_ACCOUNTANT', label: 'Shop Accountant' },
    { value: 'OWNER',           label: 'Owner' },
    { value: 'ADMIN',           label: 'Admin' },
  ];

  form: FormGroup;
  private formSub?: Subscription;

  get panelTitle(): string {
    return this.editingUser ? 'Edit User' : 'Add User';
  }

  roleLabel(value: string): string {
    return this.roles.find(r => r.value === value)?.label ?? value;
  }

  get selectedRole(): string { return this.form.get('role')?.value ?? ''; }

  get isChangingPassword(): boolean {
    return !!(this.form.get('password')?.value?.trim());
  }

  get passwordMismatch(): boolean {
    return this.isChangingPassword && !!this.form.hasError('passwordMismatch')
      && !!this.form.get('confirmPassword')?.dirty;
  }

  constructor(
    private fb: FormBuilder,
    private userService: UserService,
    private workerService: WorkerApi,
    private cdr: ChangeDetectorRef
  ) {
    this.form = this.fb.group({
      fullName:         ['', Validators.required],
      username:         ['', Validators.required],
      password:         [''],
      confirmPassword:  [''],
      adminCurrentPw:   [''],
      role:             [null, Validators.required],
      workerId:         [null],
    }, { validators: passwordsMatchValidator });
  }

  ngOnInit(): void {
    // Keep OnPush view in sync with reactive-form changes (needed for real-time validation display)
    this.formSub = this.form.valueChanges.subscribe(() => this.cdr.detectChanges());

    this.load();
    this.workerService.getAllWorkers().subscribe({
      next: (list: WorkerResponse[]) => { this.workers = list.filter(w => w.active); this.cdr.detectChanges(); },
      error: () => {},
    });
  }

  ngOnDestroy(): void {
    this.formSub?.unsubscribe();
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
    this.resetEyeToggles();
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
      confirmPassword: '',
      adminCurrentPw: '',
      role: user.role,
    });
    this.form.get('password')!.clearValidators();
    this.form.get('password')!.updateValueAndValidity();
    this.resetEyeToggles();
    this.formError = '';
    this.showPanel = true;
    this.cdr.detectChanges();
  }

  closePanel(): void {
    this.showPanel = false;
    this.editingUser = null;
    this.form.reset();
    this.resetEyeToggles();
    this.cdr.detectChanges();
  }

  private resetEyeToggles(): void {
    this.showNewPw = false;
    this.showConfirmPw = false;
    this.showAdminPw = false;
    this.showCreatePw = false;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { fullName, username, password, confirmPassword, adminCurrentPw, role, workerId } = this.form.value;
    const newPw = password?.trim();

    // Confirm password + admin verification only apply when editing an existing user
    if (this.editingUser && newPw) {
      if (confirmPassword?.trim() !== newPw) {
        this.formError = 'Passwords do not match.';
        this.cdr.detectChanges();
        return;
      }
      if (!adminCurrentPw?.trim()) {
        this.formError = 'Enter your current admin password to confirm the change.';
        this.cdr.detectChanges();
        return;
      }
    }

    this.formLoading = true;
    this.formError = '';
    this.cdr.detectChanges();

    const updatePayload = newPw
      ? { fullName, username, role, password: newPw, adminCurrentPassword: adminCurrentPw }
      : { fullName, username, role };

    const request$ = this.editingUser
      ? this.userService.update(this.editingUser.id, updatePayload)
      : this.userService.create({ fullName, username, password: newPw, role, ...(workerId ? { workerId } : {}) });

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

  activate(user: UserResponse): void {
    this.userService.activate(user.id).subscribe({
      next: () => this.load(),
      error: () => alert('Failed to activate user.')
    });
  }
}
