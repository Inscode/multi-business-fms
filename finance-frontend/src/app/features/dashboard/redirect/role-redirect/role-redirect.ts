import { Component, OnInit } from '@angular/core';
import { Auth } from '../../../../core/services/auth';
import { Router } from '@angular/router';

@Component({
  selector: 'app-role-redirect',
  imports: [],
  templateUrl: './role-redirect.html',
  styleUrl: './role-redirect.scss',
})
export class RoleRedirect implements OnInit{
  constructor(private auth: Auth, private router: Router) {}

   ngOnInit(): void {
    const role = this.auth.getRole();
    switch (role) {
      case 'ACCOUNTANT':
      case 'MAIN_ACCOUNTANT':
        this.router.navigate(['/dashboard/accountant']);
        break;
      case 'OWNER':
      case 'ADMIN':
        this.router.navigate(['/dashboard/owner']);
        break;
      case 'SHOP_ACCOUNTANT':
        this.router.navigate(['/dashboard/accountant']);
        break;
      default:
        this.router.navigate(['/login']);
    }
  }
}
