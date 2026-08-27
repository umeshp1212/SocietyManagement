import { Routes } from '@angular/router';
import { memberAuthGuard } from '@core/guards/member-auth.guard';

export const MEMBER_ROUTES: Routes = [
  {
    path: 'dashboard',
    canActivate: [memberAuthGuard],
    loadComponent: () => import('./member-dashboard/member-dashboard.component')
      .then(m => m.MemberDashboardComponent)
  }
];
