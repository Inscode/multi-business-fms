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
  // Aging report print — outside the layout so only the report prints
  {
    path: 'bills/aging/print',
    canActivate: [authGuard],
    loadComponent: () => import('./features/bills/aging-print/aging-print')
      .then(m => m.AgingPrint)
  },
  // Invoice print lives OUTSIDE the main layout so window.print() captures
  // only the invoice — no sidebar, no header.
  {
    path: 'invoicing/invoices/:id/print',
    canActivate: [authGuard],
    loadComponent: () => import('./features/invoicing/features/invoices/invoice-print/invoice-print.component')
      .then(m => m.InvoicePrintComponent)
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
      // ── Invoicing (new billing module, ported from ghanim-wholesale) ──
      {
        path: 'invoicing',
        loadComponent: () => import('./features/invoicing/invoicing-shell')
          .then(m => m.InvoicingShell),
        children: [
          { path: '', redirectTo: 'invoices', pathMatch: 'full' },
          {
            path: 'invoices',
            loadComponent: () => import('./features/invoicing/features/invoices/invoice-list/invoice-list.component')
              .then(m => m.InvoiceListComponent)
          },
          {
            path: 'invoices/new',
            loadComponent: () => import('./features/invoicing/features/invoices/invoice-form/invoice-form.component')
              .then(m => m.InvoiceFormComponent)
          },
          {
            path: 'invoices/:id/edit',
            loadComponent: () => import('./features/invoicing/features/invoices/invoice-form/invoice-form.component')
              .then(m => m.InvoiceFormComponent)
          },
          {
            path: 'invoices/:id',
            loadComponent: () => import('./features/invoicing/features/invoices/invoice-detail/invoice-detail.component')
              .then(m => m.InvoiceDetailComponent)
          },
          {
            path: 'batches',
            loadComponent: () => import('./features/invoicing/features/batches/import-batches.component')
              .then(m => m.ImportBatchesComponent)
          },
          {
            path: 'grn',
            loadComponent: () => import('./features/invoicing/features/grn/grn-list.component')
              .then(m => m.GrnListComponent)
          },
          {
            path: 'items',
            loadComponent: () => import('./features/invoicing/features/items/item-list/item-list.component')
              .then(m => m.ItemListComponent)
          },
          {
            path: 'stock-take',
            loadComponent: () => import('./features/invoicing/features/items/stock-take/stock-take.component')
              .then(m => m.StockTakeComponent)
          },
          {
            path: 'brands',
            loadComponent: () => import('./features/invoicing/features/brands/brand-list/brand-list.component')
              .then(m => m.BrandListComponent)
          },
          {
            // Admin only. Hiding the nav link alone would leave the URL open, and an
            // import writes invoices, bills and stock in one press.
            path: 'import',
            canActivate: [adminGuard],
            loadComponent: () => import('./features/invoicing/features/import/import.component')
              .then(m => m.ImportComponent)
          },
          {
            path: 'review',
            loadComponent: () => import('./features/invoicing/features/review/invoice-review.component')
              .then(m => m.InvoiceReviewComponent)
          },
        ]
      },
      {
        path: 'deliveries',
        loadComponent: () => import('./features/deliveries/deliveries-page')
          .then(m => m.DeliveriesPage)
      },
      {
        path: 'cash-flow',
        loadComponent: () => import('./features/cash-flow/cash-flow-page')
          .then(m => m.CashFlowPage)
      },
      {
        path: 'bills/aging',
        loadComponent: () => import('./features/bills/aging-report/aging-report')
          .then(m => m.AgingReport)
      },
      {
        path: 'payments/late-collections',
        loadComponent: () => import('./features/payments/late-collections/late-collections')
          .then(m => m.LateCollections)
      },
      {
        path: 'dashboard/collection-health',
        loadComponent: () => import('./features/dashboard/collection-health/collection-health')
          .then(m => m.CollectionHealth)
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
        path: 'admin',
        loadComponent: () => import('./features/admin/admin-main/admin-main')
          .then(m => m.AdminMain),
        canActivate: [adminGuard]
      },
      {
        path: 'returns',
        loadComponent: () => import('./features/returns/returns-main/returns-main')
          .then(m => m.ReturnsMain)
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
        loadComponent: () => import('./features/salary/salary-main/salary-main')
          .then(m => m.SalaryMain)
      },
      {
        path: 'stock',
        loadComponent: () => import('./features/stock/stock-tab')
          .then(m => m.StockTab)
      },
      {
        path: 'backorders',
        loadComponent: () => import('./features/backorders/backorders-main/backorders-main')
          .then(m => m.BackordersMain),
      },
      {
        path: 'workers/finance',
        loadComponent: () => import('./features/worker-finance/worker-finance-main/worker-finance-main')
          .then(m => m.WorkerFinanceMain)
      },
      {
        path: 'bill-checklist',
        loadComponent: () => import('./features/bill-checklist/bill-checklist-main/bill-checklist-main')
          .then(m => m.BillChecklistMain)
      },
      {
        path: 'customers',
        loadComponent: () => import('./features/customers/customers-page/customers-page')
          .then(m => m.CustomersPage)
      },
      {
        path: 'worker-collections',
        loadComponent: () => import('./features/worker-collections/worker-collections-page')
          .then(m => m.WorkerCollectionsPage)
      },
      {
        path: 'tasks',
        loadComponent: () => import('./features/tasks/task-board/task-board')
          .then(m => m.TaskBoard)
      },
      {
        path: 'time-log',
        loadComponent: () => import('./features/admin/time-log/time-log')
          .then(m => m.TimeLogPage),
        canActivate: [adminGuard]
      },
    ]
  },
  {
    path: 'worker',
    loadComponent: () => import('./features/worker/worker-shell/worker-shell')
      .then(m => m.WorkerShell),
    canActivate: [authGuard],
  },
  { path: '**', redirectTo: 'login' }
];