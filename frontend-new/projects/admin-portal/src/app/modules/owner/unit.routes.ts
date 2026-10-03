import { Routes } from '@angular/router';

export const UNIT_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./unit-list/unit-list.component').then(m => m.UnitListComponent)
  },
  {
    path: 'add',
    loadComponent: () => import('./unit-form/unit-form.component').then(m => m.UnitFormComponent)
  },
  {
    path: 'edit/:id',
    loadComponent: () => import('./unit-form/unit-form.component').then(m => m.UnitFormComponent)
  },
  {
    path: ':id/owners',
    loadComponent: () => import('./unit-owners/unit-owners.component').then(m => m.UnitOwnersComponent)
  },
  {
    path: ':id/history',
    loadComponent: () => import('./unit-history/unit-history.component').then(m => m.UnitHistoryComponent)
  }
];
