import { Routes } from '@angular/router';
import { PanelSupervision } from './panel-supervision/panel-supervision';
import { AlertasSupervision } from './alertas/alertas-supervision';
import { CpmSupervision } from './cpm/cpm-supervision';
import { SeccionSupervision } from './seccion-supervision';
import { Existencias } from '../almacen/existencias/existencias';
import { SalidasRegistradas } from '../almacen/despacho/salidas-registradas/salidas-registradas';
import { ReporteMermas } from './reportes/mermas/reporte-mermas';
import { ReporteCanjes } from './reportes/canjes/reporte-canjes';
import { ReporteExternos } from './reportes/externos/reporte-externos';
import { roleGuard } from '../auth/role-guard';

export const SUPERVISION_ROUTES: Routes = [
  { path: '', component: PanelSupervision, canActivate: [roleGuard], data: { roles: ['SUPERVISION'] } },
  { path: 'alertas', component: AlertasSupervision, canActivate: [roleGuard], data: { roles: ['SUPERVISION'] } },
  { path: 'cpm', component: CpmSupervision, canActivate: [roleGuard], data: { roles: ['SUPERVISION'] } },
  // Consultas de solo lectura: las mismas pantallas que usa Almacén, sin sus acciones.
  {
    path: 'consultas',
    component: SeccionSupervision,
    canActivate: [roleGuard],
    data: { roles: ['SUPERVISION'] },
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'existencias' },
      { path: 'existencias', component: Existencias },
      { path: 'salidas', component: SalidasRegistradas },
    ],
  },
  // Reportes que el documento de la Iteración 2 deja anunciados (HU18, HU19, HU20).
  {
    path: 'reportes',
    component: SeccionSupervision,
    canActivate: [roleGuard],
    data: { roles: ['SUPERVISION'] },
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'mermas' },
      { path: 'mermas', component: ReporteMermas },
      { path: 'canjes', component: ReporteCanjes },
      { path: 'instituciones', component: ReporteExternos },
    ],
  },
];
