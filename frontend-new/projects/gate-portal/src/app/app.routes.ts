import { Routes } from '@angular/router';
import { gateAuthGuard } from './core/guards/gate-auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: '/login', pathMatch: 'full' },
  {
    path: 'login',
    loadComponent: () => import('./modules/gate-login/gate-login.component')
      .then(m => m.GateLoginComponent)
  },
  {
    path: 'gate',
    canActivate: [gateAuthGuard],
    loadComponent: () => import('./modules/gate-home/gate-home.component')
      .then(m => m.GateHomeComponent)
  },
  { path: '**', redirectTo: '/login' }
];
