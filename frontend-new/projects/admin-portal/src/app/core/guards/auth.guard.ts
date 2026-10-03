import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isLoggedIn()) {
    return true;
  }

  router.navigate(['/login']);
  return false;
};

export const roleGuard = (allowedRoles: string[]): CanActivateFn => {
  return () => {
    const authService = inject(AuthService);
    const router = inject(Router);

    if (!authService.isLoggedIn()) {
      router.navigate(['/login']);
      return false;
    }

    if (authService.hasAnyRole(allowedRoles)) {
      return true;
    }

    // User is logged in but doesn't have the required role
    router.navigate(['/dashboard']);
    return false;
  };
};

/**
 * Route guard that allows access when the user holds ANY of the given permissions.
 * SUPER_ADMIN always passes as a break-glass, matching the backend
 * `hasRole('SUPER_ADMIN') or hasAuthority(...)` pattern.
 */
export const permissionGuard = (allowedPermissions: string[]): CanActivateFn => {
  return () => {
    const authService = inject(AuthService);
    const router = inject(Router);

    if (!authService.isLoggedIn()) {
      router.navigate(['/login']);
      return false;
    }

    if (authService.hasRole('SUPER_ADMIN') || authService.hasAnyPermission(allowedPermissions)) {
      return true;
    }

    // Logged in but lacks the required permission
    router.navigate(['/dashboard']);
    return false;
  };
};
