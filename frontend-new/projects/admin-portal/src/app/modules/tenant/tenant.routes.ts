import { Routes } from '@angular/router';

export const TENANT_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./tenant-list/tenant-list.component').then(m => m.TenantListComponent)
  },
  {
    path: 'register',
    loadComponent: () => import('./tenant-form/tenant-form.component').then(m => m.TenantFormComponent)
  },
  {
    path: 'bulk-upload',
    loadComponent: () => import('./tenant-bulk-upload/tenant-bulk-upload.component').then(m => m.TenantBulkUploadComponent)
  },

  {
    path: 'edit/:id',
    loadComponent: () => import('./tenant-form/tenant-form.component').then(m => m.TenantFormComponent)
  },
  {
    path: ':id',
    loadComponent: () => import('./tenant-detail/tenant-detail.component').then(m => m.TenantDetailComponent)
  }
];
