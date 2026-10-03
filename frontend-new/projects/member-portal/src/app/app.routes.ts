import { Routes } from '@angular/router';
import { memberAuthGuard } from './core/guards/member-auth.guard';

export const routes: Routes = [
  {
    path: '',
    redirectTo: '/login',
    pathMatch: 'full'
  },
  {
    path: 'login',
    loadComponent: () => import('./modules/member/member-login/member-login.component')
      .then(m => m.MemberLoginComponent)
  },
  {
    path: 'dashboard',
    canActivate: [memberAuthGuard],
    loadComponent: () => import('./modules/member/member-dashboard/member-dashboard.component')
      .then(m => m.MemberDashboardComponent)
  },
  {
    path: 'profile',
    canActivate: [memberAuthGuard],
    loadComponent: () => import('./modules/member/member-profile/member-profile.component')
      .then(m => m.MemberProfileComponent)
  },
  {
    path: 'register-tenant',
    canActivate: [memberAuthGuard],
    loadComponent: () => import('./modules/member/member-tenant-register/member-tenant-register.component')
      .then(m => m.MemberTenantRegisterComponent)
  },
  {
    path: 'apply-noc',
    canActivate: [memberAuthGuard],
    loadComponent: () => import('./modules/member/member-apply-noc/member-apply-noc.component')
      .then(m => m.MemberApplyNocComponent)
  }
];