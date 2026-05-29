import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth-guard';
import { loginGuard } from './core/guards/login-guard';
import { staffGuard } from './core/guards/staff-guard';
import { adminGuard } from './core/guards/admin-guard';
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
        path: 'dashboard/shop',
        loadComponent: () => import('./features/dashboard/shop/shop-dashboard/shop-dashboard')
          .then(m => m.ShopDashboard)
      },
      {
        path: 'owner/collect',
        loadComponent: () => import('./features/dashboard/owner/owner-collect/owner-collect')
          .then(m => m.OwnerCollect)
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
      {
        path: 'admin/users',
        loadComponent: () => import('./features/admin/users-page/users-page')
          .then(m => m.UsersPage),
        canActivate: [adminGuard]
      },
      {
        path: 'admin/edit-requests',
        loadComponent: () => import('./features/admin/edit-requests/edit-requests')
          .then(m => m.EditRequestsPage),
        canActivate: [adminGuard]
      },
      {
        path: 'admin/return-products',
        loadComponent: () => import('./features/admin/return-products/return-products')
          .then(m => m.ReturnProductsPage),
        canActivate: [adminGuard]
      },
      {
        path: 'returns',
        loadComponent: () => import('./features/returns/submit-return/submit-return')
          .then(m => m.SubmitReturn)
      },
      {
        path: 'returns/review',
        loadComponent: () => import('./features/returns/review-returns/review-returns')
          .then(m => m.ReviewReturns)
      },
      {
        path: 'expenses',
        loadComponent: () => import('./features/expenses/expense-list/expense-list')
          .then(m => m.ExpenseList)
      },
      {
        path: 'expenses/add',
        loadComponent: () => import('./features/expenses/add-expense/add-expense')
          .then(m => m.AddExpense)
      },
      {
        path: 'salary',
        loadComponent: () => import('./features/salary/enter-salary/enter-salary')
          .then(m => m.EnterSalary)
      },
      {
        path: 'salary/review',
        loadComponent: () => import('./features/salary/salary-review/salary-review')
          .then(m => m.SalaryReview)
      },
      {
        path: 'salary/recipients',
        loadComponent: () => import('./features/salary/recipients/salary-recipients')
          .then(m => m.SalaryRecipientsPage),
        canActivate: [adminGuard]
      },
    ]
  },
  { path: '**', redirectTo: 'login' }
];