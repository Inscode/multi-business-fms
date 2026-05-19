import { Routes } from '@angular/router';
import { Login } from './features/auth/login/login';
import { authGuard } from './core/guards/auth-guard';
import { MainLayoutComponent } from './layout/main-layout/main-layout';
import { loginGuard } from './core/guards/login-guard';
import { RoleRedirect } from './features/dashboard/redirect/role-redirect/role-redirect';
import { AccountantDashboard } from './features/dashboard/accountant/accountant-dashboard/accountant-dashboard';
import { OwnerDashboard } from './features/owner/owner-dashboard/owner-dashboard';
import { CreateBill } from './features/bills/create-bill/create-bill';
import { BillList } from './features/bills/bill-list/bill-list';
import { PaymentList } from './features/payments/payment-list/payment-list';
import { EnterPayment } from './features/payments/enter-payment/enter-payment';
import { BillDetail } from './features/bills/bill-detail/bill-detail';
import { StaffPage } from './features/staff/staff-page/staff-page';
import { staffGuard } from './core/guards/staff-guard';

export const routes: Routes = [
    {
        path: 'login',
        component: Login,
        canActivate: [loginGuard]
    },

    {
        path: '',
        component: MainLayoutComponent,
        canActivate: [authGuard],

        children: [
            {
                path: '', redirectTo: 'dashboard', pathMatch: 'full'
            },
            {
                path: 'dashboard', component: RoleRedirect
            },
            {
                path: 'dashboard/accountant', component: AccountantDashboard
            },
            {
                path: 'dashboard/owner', component: OwnerDashboard
            },
            { path: 'bills/create', component: CreateBill },

            {path: 'bills', component: BillList},

            {path: 'payments', component: PaymentList},

            {path: 'payments/enter', component: EnterPayment},

            {path: 'bills/:id', component: BillDetail},

            {path: 'staff', component: StaffPage, canActivate: [staffGuard]}

        ]
    },

    {path: '**', redirectTo: 'login'}

];
