import { inject } from "@angular/core";
import { CanActivateFn, Router } from "@angular/router";
import { Auth } from "../services/auth";


export const staffGuard: CanActivateFn = () => {
    const auth = inject(Auth);
    const router = inject(Router);

    const role = auth.getRole();

    if (role === 'SHOP_ACCOUNTANT' || role === 'SHOP_ACCOUNTANT') {
        router.navigate(['/dashboard']);
        return false;
    }

    return true;
}