import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  // Skip auth header for login/register/refresh endpoints
  const isAuthEndpoint = req.url.includes('/auth/login') ||
                          req.url.includes('/auth/register') ||
                          req.url.includes('/auth/refresh-token');

  let authReq = req;
  if (!isAuthEndpoint) {
    const token = authService.getToken();
    if (token) {
      authReq = req.clone({
        setHeaders: { Authorization: `Bearer ${token}` }
      });
    }
  }

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        // Token expired or invalid - logout
        authService.logout();
      } else if (error.status === 403) {
        // Forbidden - redirect to dashboard
        router.navigate(['/dashboard']);
      }
      return throwError(() => error);
    })
  );
};
