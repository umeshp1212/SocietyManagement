import { Routes } from '@angular/router';
import { permissionGuard } from '@core/guards/auth.guard';

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
    path: 'categories',
    loadComponent: () => import('./vendor-category/vendor-category.component').then(m => m.VendorCategoryComponent)
  },
  {
    // TDS management screen, reachable from the Vendor module (Requirement 10.1).
    // Gated by VENDOR_VIEW so users not authorized to view vendor/TDS data are
    // blocked from the screen (Requirement 10.3); declared before ':id' so
    // '/vendors/tds' is not swallowed by the vendor-detail param route.
    path: 'tds',
    canActivate: [permissionGuard(['VENDOR_VIEW'])],
    loadComponent: () => import('../tds/tds-list/tds-list.component').then(m => m.TdsListComponent)
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
