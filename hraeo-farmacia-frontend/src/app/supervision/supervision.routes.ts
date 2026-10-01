import { Routes } from '@angular/router';
import { PanelSupervision } from './panel-supervision/panel-supervision';
import { AlertasSupervision } from './alertas/alertas-supervision';
import { roleGuard } from '../auth/role-guard';

export const SUPERVISION_ROUTES: Routes = [
  { path: '', component: PanelSupervision, canActivate: [roleGuard], data: { roles: ['SUPERVISION'] } },
  { path: 'alertas', component: AlertasSupervision, canActivate: [roleGuard], data: { roles: ['SUPERVISION'] } },
];
