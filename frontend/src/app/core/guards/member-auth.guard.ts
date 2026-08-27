import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { MemberAuthService } from '../services/member-auth.service';

export const memberAuthGuard: CanActivateFn = () => {
  const memberAuthService = inject(MemberAuthService);
  const router = inject(Router);

  if (memberAuthService.isLoggedIn()) {
    return true;
  }

  router.navigate(['/member-login']);
  return false;
};
