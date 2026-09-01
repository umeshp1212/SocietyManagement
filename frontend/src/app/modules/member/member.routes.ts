import { Routes } from '@angular/router';
import { memberAuthGuard } from '@core/guards/member-auth.guard';

export const MEMBER_ROUTES: Routes = [
  {
    path: 'dashboard',
    canActivate: [memberAuthGuard],
    loadComponent: () => import('./member-dashboard/member-dashboard.component')
      .then(m => m.MemberDashboardComponent)
  },
  {
    path: 'profile',
    canActivate: [memberAuthGuard],
    loadComponent: () => import('./member-profile/member-profile.component')
      .then(m => m.MemberProfileComponent)
  },
  {
    path: 'register-tenant',
    canActivate: [memberAuthGuard],
    loadComponent: () => import('./member-tenant-register/member-tenant-register.component')
      .then(m => m.MemberTenantRegisterComponent)
  }
];
