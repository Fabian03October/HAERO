import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  {
    path: '',
    loadChildren: () => import('./auth/auth.routes').then((m) => m.AUTH_ROUTES),
  },
  {
    path: 'almacen',
    loadChildren: () => import('./almacen/almacen.routes').then((m) => m.ALMACEN_ROUTES),
  },
  {
    path: 'farmacia',
    loadChildren: () => import('./farmacia/farmacia.routes').then((m) => m.FARMACIA_ROUTES),
  },
  {
    path: 'supervision',
    loadChildren: () => import('./supervision/supervision.routes').then((m) => m.SUPERVISION_ROUTES),
  },
  {
    path: 'admin',
    loadChildren: () => import('./admin/admin.routes').then((m) => m.ADMIN_ROUTES),
  },
];
