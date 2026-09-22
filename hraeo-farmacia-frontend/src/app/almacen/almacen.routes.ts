import { Routes } from '@angular/router';
import { PanelAlmacen } from './panel-almacen/panel-almacen';
import { roleGuard } from '../auth/role-guard';

export const ALMACEN_ROUTES: Routes = [
  { path: '', component: PanelAlmacen, canActivate: [roleGuard], data: { roles: ['ALMACEN'] } },
];
