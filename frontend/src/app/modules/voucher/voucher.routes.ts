import { Routes } from '@angular/router';

export const VOUCHER_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./voucher-list/voucher-list.component').then(m => m.VoucherListComponent)
  },
  {
    path: 'create',
    loadComponent: () => import('./voucher-form/voucher-form.component').then(m => m.VoucherFormComponent)
  },
  {
    path: 'categories',
    loadComponent: () => import('./voucher-category/voucher-category.component').then(m => m.VoucherCategoryComponent)
  },
  {
    path: 'edit/:id',
    loadComponent: () => import('./voucher-form/voucher-form.component').then(m => m.VoucherFormComponent)
  },
  {
    path: ':id',
    loadComponent: () => import('./voucher-detail/voucher-detail.component').then(m => m.VoucherDetailComponent)
  }
];
