import { Routes } from '@angular/router';
import { PaginaError } from './shared/pagina-error/pagina-error';

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
  // Páginas de error de todo el sistema.
  { path: 'sin-acceso', component: PaginaError, data: { tipo: 'sin-acceso' } },
  { path: 'error-sistema', component: PaginaError, data: { tipo: 'error-sistema' } },
  { path: 'sin-conexion', component: PaginaError, data: { tipo: 'sin-conexion' } },
  { path: '**', component: PaginaError, data: { tipo: 'no-encontrada' } },
];
