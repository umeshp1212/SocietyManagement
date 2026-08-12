import { Routes } from '@angular/router';

export const OWNER_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./owner-list/owner-list.component').then(m => m.OwnerListComponent)
  },
  {
    path: 'add',
    loadComponent: () => import('./owner-form/owner-form.component').then(m => m.OwnerFormComponent)
  },
  {
    path: 'bulk-upload',
    loadComponent: () => import('./owner-bulk-upload/owner-bulk-upload.component').then(m => m.OwnerBulkUploadComponent)
  },
  {
    path: 'edit/:id',
    loadComponent: () => import('./owner-form/owner-form.component').then(m => m.OwnerFormComponent)
  },
  {
    path: 'transfer',
    loadComponent: () => import('./owner-transfer/owner-transfer.component').then(m => m.OwnerTransferComponent)
  },
  {
    path: ':id',
    loadComponent: () => import('./owner-detail/owner-detail.component').then(m => m.OwnerDetailComponent)
  }
];
