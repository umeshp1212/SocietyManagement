import { Routes } from '@angular/router';

export const VENDOR_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./vendor-list/vendor-list.component').then(m => m.VendorListComponent)
  },
  {
    path: 'add',
    loadComponent: () => import('./vendor-form/vendor-form.component').then(m => m.VendorFormComponent)
  },
  {
    path: 'edit/:id',
    loadComponent: () => import('./vendor-form/vendor-form.component').then(m => m.VendorFormComponent)
  },
  {
    path: ':id/ledger',
    loadComponent: () => import('./vendor-ledger/vendor-ledger.component').then(m => m.VendorLedgerComponent)
  },
  {
    path: ':id',
    loadComponent: () => import('./vendor-detail/vendor-detail.component').then(m => m.VendorDetailComponent)
  }
];
