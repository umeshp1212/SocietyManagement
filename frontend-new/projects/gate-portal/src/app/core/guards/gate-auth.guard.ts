import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Allows navigation only for a logged-in guard (SECURITY role); else redirect to login. */
export const gateAuthGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isGuardLoggedIn()) {
    return true;
  }
  router.navigate(['/login']);
  return false;
};
