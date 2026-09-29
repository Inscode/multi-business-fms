import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Auth } from '../services/auth';

const IMPORT_ROLES = ['ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT'];

export const importGuard: CanActivateFn = () => {
  const auth = inject(Auth);
  const router = inject(Router);
  if (IMPORT_ROLES.includes(auth.getRole() ?? '')) return true;

  router.navigate(['/dashboard']);
  return false;
};
