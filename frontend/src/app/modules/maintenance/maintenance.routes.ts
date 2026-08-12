import { Routes } from '@angular/router';

export const MAINTENANCE_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./bill-list/bill-list.component').then(m => m.BillListComponent)
  },
  {
    path: 'generate',
    loadComponent: () => import('./generate-bills/generate-bills.component').then(m => m.GenerateBillsComponent)
  },
  {
    path: 'charge-config',
    loadComponent: () => import('./charge-config/charge-config.component').then(m => m.ChargeConfigComponent)
  },
  {
    path: 'water-charge-config',
    loadComponent: () => import('./water-charge-config/water-charge-config.component').then(m => m.WaterChargeConfigComponent)
  },
  {
    path: 'penalties',
    loadComponent: () => import('./penalty-management/penalty-management.component').then(m => m.PenaltyManagementComponent)
  },
  {
    path: 'bill/:id',
    loadComponent: () => import('./bill-detail/bill-detail.component').then(m => m.BillDetailComponent)
  },
  {
    path: 'payments/:unitId',
    loadComponent: () => import('./payment-history/payment-history.component').then(m => m.PaymentHistoryComponent)
  }
];
