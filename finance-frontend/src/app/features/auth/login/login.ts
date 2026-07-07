import { ChangeDetectorRef, Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Auth } from '../../../core/services/auth';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-login',
  templateUrl: './login.html',
  styleUrls: ['./login.scss'],
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ]
})
export class Login {
  form: FormGroup;
  loading = false;
  errorMsg = '';
  hidePassword = true;
  shopName = environment.shopName;
  shopInitial = environment.shopName.charAt(0).toUpperCase();

  constructor(
    private fb: FormBuilder,
    private auth: Auth,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {
    this.form = this.fb.group({
      username: ['', Validators.required],
      password: ['', Validators.required],
    });
  }

  tryDemo(role: 'admin' | 'acc' | 'owner'): void {
    const users = {
      admin: { username: 'demo_admin', role: 'ADMIN',       fullName: 'Demo Admin',       id: 901 },
      acc:   { username: 'demo_acc',   role: 'ACCOUNTANT',  fullName: 'Demo Accountant',  id: 902 },
      owner: { username: 'demo_owner', role: 'OWNER',       fullName: 'Demo Owner',       id: 903 },
    };
    const u = users[role];
    const enc = (obj: object) => btoa(JSON.stringify(obj)).replace(/=/g, '');
    const token = [
      enc({ alg: 'none', typ: 'JWT' }),
      enc({ sub: u.username, role: u.role, fullName: u.fullName, id: u.id,
            iat: Math.floor(Date.now() / 1000),
            exp: Math.floor(Date.now() / 1000) + 86400 * 30 }),
      'demo',
    ].join('.');
    this.auth.saveToken(token);
    this.router.navigate(['/dashboard']);
  }

  submit(): void {
    if (this.form.invalid) return;
    this.loading = true;
    this.errorMsg = '';

    this.auth.login(this.form.value).subscribe({
      next: (res) => {
        this.auth.saveToken(res.token);
        this.router.navigate(['/dashboard']);
      },
      error: () => {
        this.errorMsg = 'Invalid username or password.';
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }
}