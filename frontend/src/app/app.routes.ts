import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  {
    path: 'login',
    loadComponent: () => import('./modules/auth/login/login.component')
      .then(m => m.LoginComponent)
  },
  {
    path: 'forgot-password',
    loadComponent: () => import('./modules/auth/forgot-password/forgot-password.component')
      .then(m => m.ForgotPasswordComponent)
  },
  {
    path: 'reset-password',
    loadComponent: () => import('./modules/auth/reset-password/reset-password.component')
      .then(m => m.ResetPasswordComponent)
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () => import('./modules/dashboard/dashboard.component')
      .then(m => m.DashboardComponent)
  },
  {
    path: 'owners',
    canActivate: [authGuard],
    loadChildren: () => import('./modules/owner/owner.routes')
      .then(m => m.OWNER_ROUTES)
  },
  {
    path: 'units',
    canActivate: [authGuard],
    loadChildren: () => import('./modules/owner/unit.routes')
      .then(m => m.UNIT_ROUTES)
  },
  {
    path: 'vendors',
    canActivate: [authGuard],
    loadChildren: () => import('./modules/vendor/vendor.routes')
      .then(m => m.VENDOR_ROUTES)
  },
  {
    path: 'tenants',
    canActivate: [authGuard],
    loadChildren: () => import('./modules/tenant/tenant.routes')
      .then(m => m.TENANT_ROUTES)
  },
  {
    path: 'vouchers',
    canActivate: [authGuard],
    loadChildren: () => import('./modules/voucher/voucher.routes')
      .then(m => m.VOUCHER_ROUTES)
  },
  {
    path: 'maintenance',
    canActivate: [authGuard],
    loadChildren: () => import('./modules/maintenance/maintenance.routes')
      .then(m => m.MAINTENANCE_ROUTES)
  },
  {
    path: 'users',
    canActivate: [roleGuard(['SUPER_ADMIN', 'CHAIRMAN', 'SECRETARY'])],
    loadChildren: () => import('./modules/auth/user.routes')
      .then(m => m.USER_ROUTES)
  },
  {
    path: 'change-password',
    canActivate: [authGuard],
    loadComponent: () => import('./modules/auth/change-password/change-password.component')
      .then(m => m.ChangePasswordComponent)
  },
  {
    path: 'settings',
    canActivate: [authGuard],
    loadComponent: () => import('./modules/settings/settings.component')
      .then(m => m.SettingsComponent)
  },
  {
    path: 'committee',
    canActivate: [authGuard],
    loadComponent: () => import('./modules/committee/committee-list.component')
      .then(m => m.CommitteeListComponent)
  },
  {
    path: 'reports',
    canActivate: [authGuard],
    loadComponent: () => import('./modules/reports/reports.component')
      .then(m => m.ReportsComponent)
  }
];