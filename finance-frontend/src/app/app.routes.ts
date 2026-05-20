import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth-guard';
import { loginGuard } from './core/guards/login-guard';
import { staffGuard } from './core/guards/staff-guard';
import { MainLayoutComponent } from './layout/main-layout/main-layout';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login').then(m => m.Login),
    canActivate: [loginGuard]
  },
  {
    path: '',
    component: MainLayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/redirect/role-redirect/role-redirect')
          .then(m => m.RoleRedirect)
      },
      {
        path: 'dashboard/accountant',
        loadComponent: () => import('./features/dashboard/accountant/accountant-dashboard/accountant-dashboard')
          .then(m => m.AccountantDashboard)
      },
      {
        path: 'dashboard/owner',
        loadComponent: () => import('./features/owner/owner-dashboard/owner-dashboard')
          .then(m => m.OwnerDashboard)
      },
      {
        path: 'bills',
        loadComponent: () => import('./features/bills/bill-list/bill-list')
          .then(m => m.BillList)
      },
      {
        path: 'bills/create',
        loadComponent: () => import('./features/bills/create-bill/create-bill')
          .then(m => m.CreateBill)
      },
      {
        path: 'bills/:id',
        loadComponent: () => import('./features/bills/bill-detail/bill-detail')
          .then(m => m.BillDetail)
      },
      {
        path: 'payments',
        loadComponent: () => import('./features/payments/payment-list/payment-list')
          .then(m => m.PaymentList)
      },
      {
        path: 'payments/enter',
        loadComponent: () => import('./features/payments/enter-payment/enter-payment')
          .then(m => m.EnterPayment)
      },
      {
        path: 'staff',
        loadComponent: () => import('./features/staff/staff-page/staff-page')
          .then(m => m.StaffPage),
        canActivate: [staffGuard]
      },
    ]
  },
  { path: '**', redirectTo: 'login' }
];