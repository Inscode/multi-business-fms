import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { Auth } from '../../core/services/auth';
import { Router } from '@angular/router';

@Component({
  selector: 'app-navbar',
  templateUrl: './navbar.html',
  styleUrl: './navbar.scss',
  imports: [
    CommonModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
    MatDividerModule,
  ]
})
export class NavbarComponent {
  @Output() toggleSidenav = new EventEmitter<void>();
  @Output() toggleCollapse = new EventEmitter<void>();

  constructor(private auth: Auth, private router: Router) {}

  get currentUser() {return this.auth.getCurrentUser();}

  onToggle(): void {
    this.toggleSidenav.emit();
    this.toggleCollapse.emit();
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/auth/login']);
  }
}
