import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { MemberAuthService } from '../services/member-auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const memberAuthService = inject(MemberAuthService);
  const router = inject(Router);

  // Skip auth header for login/register/refresh/otp endpoints
  const isAuthEndpoint = req.url.includes('/auth/login') ||
                          req.url.includes('/auth/register') ||
                          req.url.includes('/auth/refresh-token') ||
                          req.url.includes('/member/auth/');

  let authReq = req;
  if (!isAuthEndpoint) {
    // Check if this is a member API call
    const isMemberApi = req.url.includes('/member/');
    const token = isMemberApi ? memberAuthService.getToken() : authService.getToken();

    if (token) {
      authReq = req.clone({
        setHeaders: { Authorization: `Bearer ${token}` }
      });
    }
  }

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        // Check if member or admin context
        if (req.url.includes('/member/')) {
          memberAuthService.logout();
        } else {
          authService.logout();
        }
      } else if (error.status === 403) {
        router.navigate(['/dashboard']);
      }
      return throwError(() => error);
    })
  );
};
