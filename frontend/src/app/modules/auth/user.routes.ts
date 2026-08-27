import { Routes } from '@angular/router';

export const USER_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./user-list/user-list.component').then(m => m.UserListComponent)
  },
  {
    path: 'add',
    loadComponent: () => import('./user-form/user-form.component').then(m => m.UserFormComponent)
  },
  {
    path: 'edit/:id',
    loadComponent: () => import('./user-form/user-form.component').then(m => m.UserFormComponent)
  },
  {
    path: 'roles-permissions',
    loadComponent: () => import('./roles-permissions/roles-permissions.component').then(m => m.RolesPermissionsComponent)
  },
  {
    path: 'profile-requests',
    loadComponent: () => import('./profile-requests/profile-requests.component').then(m => m.ProfileRequestsComponent)
  },
  {
    path: 'registration-requests',
    loadComponent: () => import('./registration-requests/registration-requests.component').then(m => m.RegistrationRequestsComponent)
  }
];
